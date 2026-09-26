package dev.portfolio.graphrag;

import dev.portfolio.foundation.*;
import java.util.*;

/** Read-time materialized graph. Every edge retains chunk-level provenance for deletion and verification. */
public final class KnowledgeGraph {
    private final Map<String, Map<String, Object>> nodes=new LinkedHashMap<>(), edges=new LinkedHashMap<>();
    public KnowledgeGraph(List<Map<String, Object>> chunks) {
        for(Map<String, Object> chunk:chunks) {
            for(Object value:Json.list(chunk, "entities")) {
                Map<String, Object> node=Json.object(value);
                String id=Json.string(node, "id", "");
                Map<String, Object> stored=nodes.computeIfAbsent(id, k->Json.map("id", id, "name", node.get("name"), "kind", node.get("kind"), "mentions", 0, "documentIds", new ArrayList<>()));
                stored.put("mentions", Json.integer(stored, "mentions", 0)+1);
                List<Object> docs=Json.list(stored, "documentIds");
                if(!docs.contains(chunk.get("documentId")))docs.add(chunk.get("documentId"));
            }
            for(Object value:Json.list(chunk, "relations")) {
                Map<String, Object> relation=Json.object(value);
                String source=Json.string(relation, "source", ""), target=Json.string(relation, "target", ""), type=Json.string(relation, "type", "");
                String key=Checks.sha256(source+"|"+type+"|"+target).substring(0, 24);
                Map<String, Object> edge=edges.computeIfAbsent(key, k->Json.map("id", key, "source", source, "target", target, "type", type, "sources", new ArrayList<>()));
                Json.list(edge, "sources").add(Json.map("documentId", chunk.get("documentId"), "chunkId", chunk.get("id"), "title", chunk.get("title"), "evidence", relation.get("evidence"), "start", Json.integer(chunk, "start", 0)+Json.integer(relation, "offset", 0)));
            }
        }
    }
    public Map<String, Object> json() {
        return Json.map("nodes", List.copyOf(nodes.values()), "edges", List.copyOf(edges.values()), "nodeCount", nodes.size(), "edgeCount", edges.size(), "semantics", "Typed explicit triples plus separately labelled CO_OCCURS edges");
    }
    public Set<String> matchingNodes(String query) {
        String normalized=" "+Text.normalize(query)+" ";
        Set<String> terms=Text.uniqueTerms(query), ids=new LinkedHashSet<>();
        nodes.forEach((id, node)-> {
            String name=Json.string(node, "name", "");
            Set<String> names=Text.uniqueTerms(name);
            if(normalized.contains(" "+Text.normalize(name)+" ")||(!names.isEmpty()&&terms.containsAll(names)))ids.add(id);
        });
        return ids;
    }
    public Map<String, Object> node(String id) {
        Map<String, Object> node=nodes.get(id);
        if(node==null)throw Problem.missing("Graph node");
        return node;
    }
    public record Expansion(String chunkId, int hops, List<String> edgeIds) {
    }
    public List<Expansion> expand(Set<String> seeds, int maxHops) {
        record Step(String id, int depth, List<String> path) {
        }
        ArrayDeque<Step> queue=new ArrayDeque<>();
        Set<String> seen=new HashSet<>();
        seeds.forEach(id-> {
            if(nodes.containsKey(id)) {
                queue.add(new Step(id, 0, List.of()));
                seen.add(id);
            }
        });
        Map<String, Expansion> chunks=new LinkedHashMap<>();
        while(!queue.isEmpty()) {
            Step current=queue.remove();
            if(current.depth>=maxHops)continue;
            for(Map<String, Object> edge:edges.values()) {
                // Exclude weak co-occurrence from retrieval expansion; it remains available in the graph explorer.
                if(Json.string(edge, "type", "").equals("CO_OCCURS"))continue;
                String source=Json.string(edge, "source", ""), target=Json.string(edge, "target", "");
                String next=source.equals(current.id)?target:target.equals(current.id)?source:null;
                if(next==null)continue;
                List<String> path=new ArrayList<>(current.path);
                path.add(Json.string(edge, "id", ""));
                for(Object value:Json.list(edge, "sources")) {
                    String chunkId=Json.string(Json.object(value), "chunkId", "");
                    Expansion expansion=new Expansion(chunkId, current.depth+1, List.copyOf(path));
                    chunks.merge(chunkId, expansion, (a, b)->a.hops<=b.hops?a:b);
                }
                if(seen.add(next))queue.add(new Step(next, current.depth+1, path));
            }
        }
        return List.copyOf(chunks.values());
    }
    public List<Map<String, Object>> shortestPath(String from, String to) {
        node(from);
        node(to);
        if(from.equals(to))return List.of();
        record Step(String id, List<Map<String, Object>> edges) {
        }
        ArrayDeque<Step> queue=new ArrayDeque<>();
        queue.add(new Step(from, List.of()));
        Set<String> seen=new HashSet<>(Set.of(from));
        while(!queue.isEmpty()) {
            Step step=queue.remove();
            if(step.edges.size()>=6)continue;
            for(Map<String, Object> edge:edges.values()) {
                String source=Json.string(edge, "source", ""), target=Json.string(edge, "target", "");
                String next=source.equals(step.id)?target:target.equals(step.id)?source:null;
                if(next==null||!seen.add(next))continue;
                List<Map<String, Object>> path=new ArrayList<>(step.edges);
                path.add(edge);
                if(next.equals(to))return path;
                queue.add(new Step(next, path));
            }
        }
        return List.of();
    }
}
