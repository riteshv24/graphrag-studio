package dev.portfolio.graphrag;

import dev.portfolio.foundation.*;
import java.util.*;

/** Retrieval ablation against explicit human-authored relevance sets, never against invented target scores. */
public final class BenchmarkService {
    private final Store store;
    private final Jobs jobs;
    private final DocumentService documents;
    private final CollectionService collections;
    private final Retriever retriever;
    public BenchmarkService(Store store, Jobs jobs, DocumentService documents, CollectionService collections, Retriever retriever) {
        this.store=store;
        this.jobs=jobs;
        this.documents=documents;
        this.collections=collections;
        this.retriever=retriever;
        jobs.register("benchmark", this::run);
    }
    public Map<String, Object> queue(String collectionId, Map<String, Object> input, String key) {
        collections.get(collectionId);
        List<Object> cases=Json.list(input, "cases");
        if(cases.isEmpty()||cases.size()>20)throw Problem.invalid("Benchmark requires 1–20 relevance-labelled questions");
        Set<String> ids=new HashSet<>();
        documents.list(collectionId).forEach(d->ids.add(Json.string(d, "id", "")));
        List<Map<String, Object>> checked=new ArrayList<>();
        for(Object raw:cases) {
            Map<String, Object> item=Json.object(raw);
            String question=Checks.text(Json.string(item, "question", ""), "question", 1500);
            List<String> expected=Json.strings(item, "expectedDocumentIds");
            if(expected.isEmpty()||!ids.containsAll(expected))throw Problem.invalid("Every expected document must belong to this collection");
            checked.add(Json.map("question", question, "expectedDocumentIds", new LinkedHashSet<>(expected)));
        }
        int topK=Checks.range(Json.integer(input, "topK", 5), "topK", 1, 12);
        // Freeze the corpus at submission so concurrent ingestion cannot change the experiment midway.
        return jobs.enqueue("benchmark", Json.map("collection", collections.get(collectionId), "chunks", documents.chunks(collectionId), "cases", checked, "topK", topK), key);
    }
    private Map<String, Object> run(Jobs.Context context, Map<String, Object> payload) throws Exception {
        Map<String, Object> collection=Json.object(payload.get("collection"));
        List<Map<String, Object>> chunks=Json.list(payload, "chunks").stream().map(Json::object).toList();
        List<Object> cases=Json.list(payload, "cases");
        List<Map<String, Object>> rows=new ArrayList<>();
        int completed=0;
        for(String mode:List.of("lexical", "vector", "hybrid"))for(Object raw:cases) {
            context.checkCancelled();
            Map<String, Object> item=Json.object(raw);
            long start=System.nanoTime();
            Retriever.Retrieval result=retriever.search(Json.string(item, "question", ""), chunks, Json.string(collection, "embeddingProvider", "hash"), mode, (int)Json.integer(payload, "topK", 5), mode.equals("hybrid")?2:0);
            Set<String> expected=new HashSet<>(Json.strings(item, "expectedDocumentIds"));
            List<String> found=result.sources().stream().map(s->Json.string(s, "documentId", "")).distinct().toList();
            long relevant=found.stream().filter(expected::contains).count();
            double reciprocal=0;
            for(int i=0; i<found.size(); i++)if(expected.contains(found.get(i))) {
                reciprocal=1d/(i+1);
                break;
            }
            rows.add(Json.map("mode", mode, "question", item.get("question"), "recall", (double)relevant/expected.size(), "reciprocalRank", reciprocal, "retrievedDocuments", found, "expectedDocumentIds", expected, "latencyMs", (System.nanoTime()-start)/1_000_000));
            context.progress(++completed, cases.size()*3, "Evaluated "+mode+" retrieval");
        }
        List<Map<String, Object>> summary=new ArrayList<>();
        for(String mode:List.of("lexical", "vector", "hybrid")) {
            List<Map<String, Object>> selected=rows.stream().filter(row->mode.equals(row.get("mode"))).toList();
            summary.add(Json.map("mode", mode, "recallAtK", Text.round(selected.stream().mapToDouble(row->Json.number(row, "recall", 0)).average().orElse(0)), "mrr", Text.round(selected.stream().mapToDouble(row->Json.number(row, "reciprocalRank", 0)).average().orElse(0)), "meanLatencyMs", Text.round(selected.stream().mapToDouble(row->Json.number(row, "latencyMs", 0)).average().orElse(0))));
        }
        Map<String, Object> result=Json.map("id", context.id(), "collectionId", collection.get("id"), "topK", payload.get("topK"), "corpusChunks", chunks.size(), "rows", rows, "summary", summary, "createdAt", Checks.now(), "note", "Small fixture experiment; not a claim that hybrid retrieval always outperforms the other modes");
        store.write(tx-> {
            tx.put("benchmarks", context.id(), result);
            return null;
        });
        return Json.map("benchmarkId", context.id());
    }
    public List<Map<String, Object>> list(String collectionId) {
        return store.read(tx->tx.scan("benchmarks").stream().filter(b->collectionId.equals(b.get("collectionId"))).toList());
    }
}
