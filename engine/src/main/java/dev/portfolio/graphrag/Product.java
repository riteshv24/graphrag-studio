package dev.portfolio.graphrag;

import dev.portfolio.foundation.*;
import java.util.*;

public final class Product implements Application {
    private final Store store;
    private final Jobs jobs;
    private final Router router=new Router();
    private final CollectionService collections;
    private final DocumentService documents;
    private final QueryService queries;
    private final BenchmarkService benchmarks;
    public Product(Store store, ModelClient model) {
        this.store=store;
        jobs=new Jobs(store);
        collections=new CollectionService(store);
        documents=new DocumentService(store, jobs, model);
        queries=new QueryService(store, collections, documents, model);
        benchmarks=new BenchmarkService(store, jobs, documents, collections, queries.retriever());
        commonRoutes(model);
        router.on("GET", "/api/collections", q->Router.Response.json(collections.list()));
        router.on("POST", "/api/collections", q->Router.Response.created(collections.create(q.json())));
        router.on("GET", "/api/collections/{id}", q->Router.Response.json(collections.get(q.param("id"))));
        router.on("GET", "/api/collections/{id}/documents", q->Router.Response.json(documents.list(q.param("id"))));
        router.on("POST", "/api/collections/{id}/documents", q->Router.Response.accepted(documents.ingest(q.param("id"), q.json(), q.header("idempotency-key"))));
        router.on("GET", "/api/documents/{id}", q->Router.Response.json(documents.get(q.param("id"))));
        router.on("DELETE", "/api/documents/{id}", q->Router.Response.json(documents.delete(q.param("id"))));
        router.on("POST", "/api/documents/{id}/reindex", q->Router.Response.accepted(documents.reindex(q.param("id"), q.header("idempotency-key"))));
        router.on("GET", "/api/collections/{id}/graph", q->Router.Response.json(graph(q.param("id")).json()));
        router.on("GET", "/api/collections/{id}/path", q-> {
            Map<String, String> query=q.query();
            return Router.Response.json(Json.map("edges", graph(q.param("id")).shortestPath(query.getOrDefault("from", ""), query.getOrDefault("to", "")), "traversal", "undirected exploration, at most six edges"));
        });
        router.on("POST", "/api/collections/{id}/query", q-> {
            try {
                return Router.Response.json(queries.query(q.param("id"), q.json()));
            } catch(RuntimeException e) {
                throw e;
            } catch(Exception e) {
                throw new Problem(502, "query_failed", "A configured model or embedding request failed; check the local provider");
            }
        });
        router.on("GET", "/api/collections/{id}/queries", q->Router.Response.json(queries.history(q.param("id"))));
        router.on("PUT", "/api/queries/{id}/feedback", q->Router.Response.json(queries.feedback(q.param("id"), q.json())));
        router.on("POST", "/api/collections/{id}/benchmarks", q->Router.Response.accepted(benchmarks.queue(q.param("id"), q.json(), q.header("idempotency-key"))));
        router.on("GET", "/api/collections/{id}/benchmarks", q->Router.Response.json(benchmarks.list(q.param("id"))));
        router.on("GET", "/api/collections/{id}/benchmark-fixture", q->Router.Response.json(Json.map("cases", DemoData.relevance(documents.list(q.param("id"))), "topK", 5)));
        router.on("GET", "/api/collections/{id}/graph.json", q->Router.Response.download("knowledge-graph.json", "application/json", Json.write(graph(q.param("id")).json())));
        router.on("GET", "/api/collections/{id}/graph.graphml", q->Router.Response.download("knowledge-graph.graphml", "application/xml", GraphExport.graphml(graph(q.param("id")).json())));
        router.on("GET", "/api/collections/{id}/graph.cypher", q->Router.Response.download("knowledge-graph.cypher", "text/plain", GraphExport.cypher(q.param("id"), graph(q.param("id")).json())));
    }
    private KnowledgeGraph graph(String id) {
        return new KnowledgeGraph(documents.chunks(Checks.id(id)));
    }
    public Store store() {
        return store;
    }
    public Jobs jobs() {
        return jobs;
    }
    public Router router() {
        return router;
    }
    public String name() {
        return "GraphRAG Studio";
    }
    public CollectionService collections() {
        return collections;
    }
    public DocumentService documents() {
        return documents;
    }
    public QueryService queries() {
        return queries;
    }
    public BenchmarkService benchmarks() {
        return benchmarks;
    }
    public void seed() {
        if(store.read(tx->tx.get("settings", "demo_seeded").isPresent()))return;
        Map<String, Object> collection=collections.create(Json.map("name", "Northstar architecture", "description", "A fictional service graph for exploring evidence across system design documents.", "embeddingProvider", "hash", "chunkSize", 1100, "overlap", 120, "aliases", Json.map("payments", "Payments Service", "identity", "Identity Service", "checkout", "Checkout Service", "inventory", "Inventory Service", "Postgres", "PostgreSQL")));
        int i=0;
        for(Map<String, Object> document:DemoData.documents())documents.ingest(Json.string(collection, "id", ""), document, "seed-doc-"+(i++));
        store.write(tx-> {
            tx.put("settings", "demo_seeded", Json.map("done", true));
            return null;
        });
    }
}
