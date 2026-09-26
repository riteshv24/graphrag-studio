package dev.portfolio.tests;

import dev.portfolio.foundation.*;
import dev.portfolio.graphrag.*;
import java.util.*;
import static dev.portfolio.tests.TestSupport.*;

public final class DomainTests {
    public static void run() throws Exception {
        test("Chunk boundaries reproduce exact source substrings", ()-> {
            String text=("Payments Service owns a ledger. 🌨\n").repeat(40);
            var chunks=new Chunker().split(text, 250, 40);
            truth(chunks.size()>2);
            for(var c:chunks)equal(text.substring(c.start(), c.end()), c.text());
        });
        test("Chunk overlap is bounded and progresses monotonically", ()-> {
            var chunks=new Chunker().split("abcdefghij ".repeat(120), 220, 50);
            for(int i=1; i<chunks.size(); i++) {
                truth(chunks.get(i).start()>chunks.get(i-1).start());
                truth(chunks.get(i).start()<chunks.get(i-1).end());
            }
        });
        test("Chunking rejects excessive overlap", ()->problem(400, ()->new Chunker().split("text", 300, 200)));
        test("Hash vectors are deterministic and unit-normalized", ()-> {
            var a=Embeddings.hash("Payments Service ledger accounting", 256);
            var b=Embeddings.hash("Payments Service ledger accounting", 256);
            equal(a, b);
            near(1, Embeddings.cosine(a, b), 1e-9);
            near(1, Math.sqrt(a.stream().mapToDouble(v->v*v).sum()), 1e-9);
        });
        test("Zero vector cosine is zero rather than NaN", ()->equal(0, Embeddings.cosine(List.of(0d, 0d), List.of(1d, 0d))));
        test("Mixed vector dimensions are rejected", ()->problem(409, ()->Embeddings.cosine(List.of(1d), List.of(1d, 0d))));
        test("BM25 ranks the matching document before unrelated text", ()-> {
            var scores=new Bm25(Map.of("a", "payment ledger refund", "b", "flower garden soil")).scores("ledger refund");
            truth(scores.containsKey("a"));
            truth(!scores.containsKey("b"));
        });
        test("Hash provider explicitly identifies its non-semantic vector space", ()->equal("lexical-hash-v1:256", new Embeddings(offlineModel()).embed("hello", "hash").signature()));
        test("Explicit relationship extraction retains source evidence", ()-> {
            var result=new EntityExtractor().extract("Payments Service -> DEPENDS_ON -> Identity Service", Map.of());
            truth(result.relations().stream().anyMatch(r->r.type().equals("DEPENDS_ON")));
            truth(result.entities().size()>=2);
        });
        test("Relationship type tokens are not extracted as entity names", ()-> {
            var result=new EntityExtractor().extract("Platform Team -> OWNS -> Identity Service", Map.of());
            truth(result.entities().stream().noneMatch(entity->entity.name().equals("OWNS")));
        });
        test("Weak co-occurrence is distinguished from typed relationships", ()-> {
            var result=new EntityExtractor().extract("Payments Service and Identity Service share a meeting.", Map.of());
            truth(result.relations().stream().noneMatch(r->r.type().equals("DEPENDS_ON")));
        });
        test("Collection validates aliases and provider choice", ()-> {
            var service=new CollectionService(new MemoryStore());
            problem(400, ()->service.create(Json.map("name", "bad", "embeddingProvider", "magic")));
            problem(400, ()->service.create(Json.map("name", "bad", "aliases", Json.map("a", 42))));
        });
        try(Product product=new Product(new MemoryStore(), offlineModel())) {
            product.start();
            allJobs(product);
            var collections=list(product, "/api/collections");
            String collectionId=id(collections.get(0));
            String base="/api/collections/"+collectionId;
            var docs=list(product, base+"/documents");
            var graph=call(product, "GET", base+"/graph", null);
            test("Indexing commits all sample documents and chunk counts", ()-> {
                equal(8, docs.size());
                truth(docs.stream().allMatch(d->Json.integer(d, "version", 0)==1));
                truth(docs.stream().allMatch(d->Json.integer(d, "chunkCount", 0)>0));
            });
            test("Knowledge graph has source-bearing typed edges", ()-> {
                truth(Json.integer(graph, "nodeCount", 0)>5);
                truth(Json.list(graph, "edges").stream().map(Json::object).allMatch(edge->!Json.list(edge, "sources").isEmpty()));
            });
            test("Hybrid query returns ranked sources with explanations", ()-> {
                var q=call(product, "POST", base+"/query", Json.map("question", "Who owns Payments Service and what does it depend on?", "mode", "hybrid", "hops", 2));
                truth(!Json.bool(q, "abstained", true));
                truth(!Json.list(q, "sources").isEmpty());
                var source=object(Json.list(q, "sources").get(0));
                truth(source.containsKey("explanation"));
                truth(source.containsKey("documentVersion"));
                truth(Json.string(q, "answer", "").contains("[S"));
            });
            test("Unrelated question abstains instead of hallucinating", ()-> {
                var q=call(product, "POST", base+"/query", Json.map("question", "quasarmagnonx interstellarzebracorn", "mode", "hybrid"));
                truth(Json.bool(q, "abstained", false));
                equal(0, Json.list(q, "sources").size());
            });
            test("Exact-content reingest deduplicates documents", ()-> {
                var doc=call(product, "GET", "/api/documents/"+id(docs.get(0)), null);
                var result=call(product, "POST", base+"/documents", Json.map("title", "duplicate title", "text", doc.get("text")));
                truth(Json.bool(result, "duplicate", false));
                equal(8, list(product, base+"/documents").size());
            });
            test("Reindex increments version only after completion", ()-> {
                String docId=id(docs.get(0));
                var job=call(product, "POST", "/api/documents/"+docId+"/reindex", Map.of());
                equal("SUCCEEDED", await(product.jobs(), id(job)).get("status"));
                equal(2, call(product, "GET", "/api/documents/"+docId, null).get("version"));
            });
            test("Query feedback rejects stale revisions", ()-> {
                var q=call(product, "POST", base+"/query", Json.map("question", "Payments Service"));
                String path="/api/queries/"+id(q)+"/feedback";
                var body=Json.map("helpful", true, "note", "Source verified", "expectedRevision", 1);
                equal(2, call(product, "PUT", path, body).get("revision"));
                problem(409, ()->call(product, "PUT", path, body));
            });
            test("Benchmark computes 3-method relevance ablation", ()-> {
                var fixture=call(product, "GET", base+"/benchmark-fixture", null);
                var job=call(product, "POST", base+"/benchmarks", fixture);
                equal("SUCCEEDED", await(product.jobs(), id(job)).get("status"));
                var b=list(product, base+"/benchmarks").get(0);
                equal(3, Json.list(b, "summary").size());
                equal(Json.list(fixture, "cases").size()*3, Json.list(b, "rows").size());
                for(var row:Json.list(b, "summary")) {
                    double recall=Json.number(object(row), "recallAtK", -1);
                    truth(recall>=0&&recall<=1);
                }
            });
            test("GraphML and Cypher exports preserve relationship semantics", ()-> {
                truth(GraphExport.graphml(graph).contains("<graphml"));
                truth(GraphExport.cypher(collectionId, graph).contains("MERGE"));
                truth(GraphExport.graphml(graph).contains("DEPENDS_ON"));
            });
            test("Query source offsets point into the stored source text", ()-> {
                var q=call(product, "POST", base+"/query", Json.map("question", "Identity Service", "mode", "lexical"));
                for(var raw:Json.list(q, "sources")) {
                    var s=object(raw);
                    var doc=call(product, "GET", "/api/documents/"+s.get("documentId"), null);
                    equal(Json.string(doc, "text", "").substring((int)Json.integer(s, "start", 0), (int)Json.integer(s, "end", 0)), s.get("text"));
                }
            });
            test("Deleting indexed document removes its live graph provenance", ()-> {
                String docId=id(docs.get(0));
                call(product, "DELETE", "/api/documents/"+docId, null);
                equal(7, list(product, base+"/documents").size());
                var updated=call(product, "GET", base+"/graph", null);
                truth(!Json.write(updated).contains(docId));
            });
            test("Top-K and traversal depth are bounded", ()-> {
                problem(400, ()->call(product, "POST", base+"/query", Json.map("question", "payments", "topK", 99)));
                problem(400, ()->call(product, "POST", base+"/query", Json.map("question", "payments", "hops", 99)));
            });
        }
    }
}
