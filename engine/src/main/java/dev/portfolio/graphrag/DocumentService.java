package dev.portfolio.graphrag;

import dev.portfolio.foundation.*;
import java.util.*;

/** Indexing is computed off-transaction, then committed atomically with an index-generation check. */
public final class DocumentService {
    private final Store store;
    private final Jobs jobs;
    private final Embeddings embeddings;
    private final Chunker chunker=new Chunker();
    private final EntityExtractor extractor=new EntityExtractor();
    public DocumentService(Store store, Jobs jobs, ModelClient model) {
        this.store=store;
        this.jobs=jobs;
        embeddings=new Embeddings(model);
        jobs.register("index-document", this::index);
    }
    public Map<String, Object> ingest(String collectionId, Map<String, Object> input, String key) {
        return queue(collectionId, null, input, key);
    }
    public Map<String, Object> reindex(String id, String key) {
        Map<String, Object> doc=get(id);
        return queue(Json.string(doc, "collectionId", ""), id, doc, key);
    }
    private Map<String, Object> queue(String collectionId, String existingId, Map<String, Object> input, String key) {
        Checks.id(collectionId);
        String title=Checks.text(Json.string(input, "title", ""), "title", 160), text=Checks.text(Json.string(input, "text", ""), "text", 120000), source=Checks.optional(Json.string(input, "source", "local text"), "source", 500);
        String checksum=Checks.sha256(text);
        return store.write(tx-> {
            Map<String, Object> collection=tx.require("collections", collectionId);
            if(existingId==null) {
                Optional<Map<String, Object>> duplicate=tx.scan("documents").stream().filter(d->collectionId.equals(d.get("collectionId"))&&checksum.equals(d.get("checksum"))).findFirst();
                if(duplicate.isPresent())return Json.map("duplicate", true, "documentId", duplicate.get().get("id"), "id", duplicate.get().get("indexJobId"));
                if(tx.scan("documents").stream().filter(d->collectionId.equals(d.get("collectionId"))).count()>=80)throw Problem.invalid("Collection supports at most 80 documents");
            }
            Map<String, Object> payload=Json.map("collection", collection, "documentId", existingId, "title", title, "text", text, "source", source, "checksum", checksum);
            Map<String, Object> job=jobs.enqueue(tx, "index-document", payload, key);
            String jobId=Json.string(job, "id", ""), documentId=existingId==null?jobId:existingId;
            Map<String, Object> doc=tx.get("documents", documentId).orElse(Json.map("id", documentId, "collectionId", collectionId, "chunks", List.of(), "version", 0, "createdAt", Checks.now()));
            doc.putAll(Json.map("title", title, "text", text, "source", source, "checksum", checksum, "indexJobId", jobId, "updatedAt", Checks.now()));
            tx.put("documents", documentId, doc);
            job.put("documentId", documentId);
            return job;
        });
    }
    private Map<String, Object> index(Jobs.Context context, Map<String, Object> payload) throws Exception {
        Map<String, Object> collection=Json.object(payload.get("collection"));
        String docId=Json.string(payload, "documentId", context.id()), provider=Json.string(collection, "embeddingProvider", "hash");
        Map<String, String> aliases=new LinkedHashMap<>();
        Json.object(collection.get("aliases")).forEach((k, v)->aliases.put(k, (String)v));
        List<Chunker.Chunk> chunks=chunker.split(Json.string(payload, "text", ""), (int)Json.integer(collection, "chunkSize", 900), (int)Json.integer(collection, "overlap", 120));
        List<Map<String, Object>> indexed=new ArrayList<>();
        String signature="";
        for(Chunker.Chunk chunk:chunks) {
            context.checkCancelled();
            Embeddings.Vector vector=embeddings.embed(chunk.text(), provider);
            if(!signature.isEmpty()&&!signature.equals(vector.signature()))throw Problem.conflict("Embedding provider changed dimensions during indexing");
            signature=vector.signature();
            EntityExtractor.Extraction graph=extractor.extract(chunk.text(), aliases);
            Map<String, Object> item=chunk.json();
            item.putAll(Json.map("id", docId+"-"+chunk.ordinal(), "documentId", docId, "title", payload.get("title"), "vector", vector.values(), "embeddingSignature", signature, "entities", graph.entities().stream().map(EntityExtractor.Entity::json).toList(), "relations", graph.relations().stream().map(EntityExtractor.Relation::json).toList()));
            indexed.add(item);
            context.progress(indexed.size(), chunks.size(), "Indexed chunk "+indexed.size()+" of "+chunks.size());
        }
        String finalSignature=signature;
        store.write(tx-> {
            Map<String, Object> doc=tx.require("documents", docId);
            if(!context.id().equals(Json.string(doc, "indexJobId", "")))throw Problem.conflict("A newer indexing generation replaced this job");
            doc.put("chunks", indexed);
            doc.put("embeddingSignature", finalSignature);
            doc.put("version", Json.integer(doc, "version", 0)+1);
            doc.put("indexedAt", Checks.now());
            tx.put("documents", docId, doc);
            Audit.record(tx, "document.indexed", docId);
            return null;
        });
        return Json.map("documentId", docId, "chunks", indexed.size(), "embeddingSignature", signature);
    }
    public Map<String, Object> get(String id) {
        return store.read(tx-> {
            Map<String, Object> doc=tx.require("documents", Checks.id(id));
            return status(tx, doc);
        });
    }
    public List<Map<String, Object>> list(String collectionId) {
        Checks.id(collectionId);
        return store.read(tx-> {
            tx.require("collections", collectionId);
            return tx.scan("documents").stream().filter(d->collectionId.equals(d.get("collectionId"))).map(d-> {
                status(tx, d);
                d.put("chunkCount", Json.list(d, "chunks").size());
                d.remove("chunks");
                d.remove("text");
                return d;
            }).toList();
        });
    }
    private Map<String, Object> status(Store.Tx tx, Map<String, Object> doc) {
        Map<String, Object> job=tx.require("jobs", Json.string(doc, "indexJobId", ""));
        doc.put("status", job.get("status"));
        doc.put("error", job.getOrDefault("error", ""));
        return doc;
    }
    public List<Map<String, Object>> chunks(String collectionId) {
        return store.read(tx-> {
            tx.require("collections", collectionId);
            List<Map<String, Object>> chunks=new ArrayList<>();
            for(Map<String, Object> doc:tx.scan("documents"))if(collectionId.equals(doc.get("collectionId")))for(Object item:Json.list(doc, "chunks")) {
                Map<String, Object> chunk=Json.object(item);
                chunk.put("documentVersion", doc.get("version"));
                chunks.add(chunk);
            }
            if(chunks.size()>2500)throw new Problem(413, "corpus_large", "Collection exceeds 2500 chunks");
            return chunks;
        });
    }
    public Map<String, Object> delete(String id) {
        return store.write(tx-> {
            Map<String, Object> doc=tx.require("documents", Checks.id(id));
            String jobId=Json.string(doc, "indexJobId", "");
            Map<String, Object> job=tx.require("jobs", jobId);
            String state=Json.string(job, "status", "");
            if(Set.of("QUEUED", "RUNNING").contains(state))throw Problem.conflict("Cancel indexing and wait for a terminal state before deleting");
            tx.delete("documents", id);
            Audit.record(tx, "document.deleted", id);
            return Json.map("deleted", true, "documentId", id);
        });
    }
}
