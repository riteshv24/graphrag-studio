package dev.portfolio.graphrag;

import dev.portfolio.foundation.*;
import java.util.*;

/** Hybrid ranking with reciprocal-rank fusion and bounded, provenance-bearing graph expansion. */
public final class Retriever {
    public record Retrieval(List<Map<String, Object>> sources, Map<String, Object> diagnostics) {
    }
    private final Embeddings embeddings;
    public Retriever(ModelClient model) {
        embeddings=new Embeddings(model);
    }
    public Retrieval search(String question, List<Map<String, Object>> chunks, String provider, String mode, int topK, int hops) throws Exception {
        Checks.oneOf(mode, "mode", "lexical", "vector", "hybrid");
        Checks.range(topK, "topK", 1, 12);
        Checks.range(hops, "hops", 0, 2);
        Map<String, Map<String, Object>> byId=new LinkedHashMap<>();
        Map<String, String> text=new LinkedHashMap<>();
        for(Map<String, Object> chunk:chunks) {
            String id=Json.string(chunk, "id", "");
            byId.put(id, chunk);
            text.put(id, Json.string(chunk, "title", "")+" "+Json.string(chunk, "text", ""));
        }
        Map<String, Double> lexical=new Bm25(text).scores(question), cosine=new LinkedHashMap<>();
        if(!mode.equals("lexical")&&!chunks.isEmpty()) {
            Embeddings.Vector query=embeddings.embed(question, provider);
            for(Map<String, Object> chunk:chunks) {
                if(!query.signature().equals(Json.string(chunk, "embeddingSignature", "")))throw Problem.conflict("Embedding space changed; reindex every document with the current model before querying");
                double score=Embeddings.cosine(query.values(), Embeddings.values(chunk));
                if(score>.03)cosine.put(Json.string(chunk, "id", ""), score);
            }
        }
        List<String> lexicalRank=rank(lexical), vectorRank=rank(cosine);
        Map<String, Double> fused=new LinkedHashMap<>();
        if(!mode.equals("vector"))addRanks(fused, lexicalRank, 1);
        if(!mode.equals("lexical"))addRanks(fused, vectorRank, 1);
        Map<String, KnowledgeGraph.Expansion> graphEvidence=new HashMap<>();
        if(mode.equals("hybrid")&&hops>0) {
            KnowledgeGraph graph=new KnowledgeGraph(chunks);
            Set<String> seeds=graph.matchingNodes(question);
            if(seeds.isEmpty()&&!lexicalRank.isEmpty())for(Object node:Json.list(byId.get(lexicalRank.get(0)), "entities"))seeds.add(Json.string(Json.object(node), "id", ""));
            for(KnowledgeGraph.Expansion expansion:graph.expand(seeds, hops)) {
                graphEvidence.put(expansion.chunkId(), expansion);
                fused.merge(expansion.chunkId(), .6/(60+expansion.hops()), Double::sum);
            }
        }
        boolean evidenceAbsent=lexical.isEmpty()&&(provider.equals("hash")||cosine.values().stream().mapToDouble(Double::doubleValue).max().orElse(0)<.55);
        if(evidenceAbsent)return new Retrieval(List.of(), Json.map("reason", "No lexical evidence; no sufficiently strong configured semantic vector match", "corpusChunks", chunks.size(), "mode", mode));
        List<Map<String, Object>> sources=new ArrayList<>();
        for(String id:rank(fused).stream().limit(topK).toList()) {
            Map<String, Object> chunk=byId.get(id);
            if(chunk==null)continue;
            KnowledgeGraph.Expansion expansion=graphEvidence.get(id);
            sources.add(Json.map("citation", "S"+(sources.size()+1), "chunkId", id, "documentId", chunk.get("documentId"), "documentVersion", chunk.get("documentVersion"), "title", chunk.get("title"), "text", chunk.get("text"), "start", chunk.get("start"), "end", chunk.get("end"), "score", Text.round(fused.get(id)), "explanation", Json.map("bm25", Text.round(lexical.getOrDefault(id, 0d)), "cosine", Text.round(cosine.getOrDefault(id, 0d)), "lexicalRank", lexicalRank.contains(id)?lexicalRank.indexOf(id)+1:null, "vectorRank", vectorRank.contains(id)?vectorRank.indexOf(id)+1:null, "graphHops", expansion==null?null:expansion.hops(), "edgePath", expansion==null?List.of():expansion.edgeIds())));
        }
        return new Retrieval(sources, Json.map("corpusChunks", chunks.size(), "lexicalMatches", lexical.size(), "vectorMatches", cosine.size(), "graphExpandedChunks", graphEvidence.size(), "mode", mode, "embeddingProvider", provider, "formula", "sum(weight / (60 + rank)); graph expansion weight 0.6"));
    }
    private static List<String> rank(Map<String, Double> scores) {
        return scores.entrySet().stream().sorted(Map.Entry.<String, Double>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey())).map(Map.Entry::getKey).limit(40).toList();
    }
    private static void addRanks(Map<String, Double> scores, List<String> ranked, double weight) {
        for(int i=0; i<ranked.size(); i++)scores.merge(ranked.get(i), weight/(60+i+1), Double::sum);
    }
}
