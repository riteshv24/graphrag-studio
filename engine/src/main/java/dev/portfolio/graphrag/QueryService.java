package dev.portfolio.graphrag;

import dev.portfolio.foundation.*;
import java.util.*;

public final class QueryService {
    private final Store store;
    private final CollectionService collections;
    private final DocumentService documents;
    private final Retriever retriever;
    private final AnswerComposer composer;
    public QueryService(Store store, CollectionService collections, DocumentService documents, ModelClient model) {
        this.store=store;
        this.collections=collections;
        this.documents=documents;
        retriever=new Retriever(model);
        composer=new AnswerComposer(model);
    }
    public Map<String, Object> query(String collectionId, Map<String, Object> input) throws Exception {
        String question=Checks.text(Json.string(input, "question", ""), "question", 2000), mode=Checks.oneOf(Json.string(input, "mode", "hybrid"), "mode", "lexical", "vector", "hybrid");
        int topK=Checks.range(Json.integer(input, "topK", 5), "topK", 1, 12), hops=Checks.range(Json.integer(input, "hops", 1), "hops", 0, 2);
        boolean generate=Json.bool(input, "generate", false);
        Map<String, Object> collection=collections.get(collectionId);
        long start=System.nanoTime();
        List<Map<String, Object>> chunks=documents.chunks(collectionId);
        Retriever.Retrieval retrieval=retriever.search(question, chunks, Json.string(collection, "embeddingProvider", "hash"), mode, topK, hops);
        AnswerComposer.Answer answer=composer.compose(question, retrieval.sources(), generate);
        String id=Checks.uuid();
        Map<String, Object> result=Json.map("id", id, "collectionId", collectionId, "question", question, "answer", answer.text(), "abstained", answer.abstained(), "answerMode", answer.mode(), "note", answer.note(), "sources", retrieval.sources(), "diagnostics", retrieval.diagnostics(), "latencyMs", (System.nanoTime()-start)/1_000_000, "createdAt", Checks.now(), "feedback", Json.map("helpful", null, "note", ""), "revision", 1);
        store.write(tx-> {
            tx.put("queries", id, result);
            List<Map<String, Object>> old=tx.scan("queries");
            if(old.size()>200)old.stream().sorted(Comparator.comparing(q->Json.string(q, "createdAt", ""))).limit(old.size()-200).forEach(q->tx.delete("queries", Json.string(q, "id", "")));
            return null;
        });
        return result;
    }
    public List<Map<String, Object>> history(String collectionId) {
        return store.read(tx->tx.scan("queries").stream().filter(q->collectionId.equals(q.get("collectionId"))).sorted(Comparator.comparing((Map<String, Object> q)->Json.string(q, "createdAt", "")).reversed()).limit(50).toList());
    }
    public Map<String, Object> feedback(String id, Map<String, Object> input) {
        boolean helpful=Json.bool(input, "helpful", false);
        String note=Checks.optional(Json.string(input, "note", ""), "note", 1200);
        return store.write(tx-> {
            Map<String, Object> query=tx.require("queries", Checks.id(id));
            long revision=Json.integer(query, "revision", 1);
            if(Json.integer(input, "expectedRevision", -1)!=revision)throw Problem.conflict("Feedback changed; reload before updating");
            query.put("feedback", Json.map("helpful", helpful, "note", note));
            query.put("revision", revision+1);
            tx.put("queries", id, query);
            return query;
        });
    }
    public Retriever retriever() {
        return retriever;
    }
}
