package dev.portfolio.graphrag;

import dev.portfolio.foundation.*;
import java.util.*;

/** Interchange exports, not a claim that Neo4j is the primary persistence engine. */
public final class GraphExport {
    private GraphExport() {
    }
    public static String graphml(Map<String, Object> graph) {
        StringBuilder out=new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<graphml xmlns=\"http://graphml.graphdrawing.org/xmlns\">\n<key id=\"name\" for=\"node\" attr.name=\"name\" attr.type=\"string\"/>\n<key id=\"type\" for=\"edge\" attr.name=\"type\" attr.type=\"string\"/>\n<graph id=\"knowledge\" edgedefault=\"directed\">\n");
        for(Object raw:Json.list(graph, "nodes")) {
            Map<String, Object> node=Json.object(raw);
            out.append("<node id=\"").append(Text.xml(Json.string(node, "id", ""))).append("\"><data key=\"name\">").append(Text.xml(Json.string(node, "name", ""))).append("</data></node>\n");
        }
        for(Object raw:Json.list(graph, "edges")) {
            Map<String, Object> edge=Json.object(raw);
            out.append("<edge id=\"").append(Text.xml(Json.string(edge, "id", ""))).append("\" source=\"").append(Text.xml(Json.string(edge, "source", ""))).append("\" target=\"").append(Text.xml(Json.string(edge, "target", ""))).append("\"><data key=\"type\">").append(Text.xml(Json.string(edge, "type", ""))).append("</data></edge>\n");
        }
        return out.append("</graph></graphml>\n").toString();
    }
    public static String cypher(String collectionId, Map<String, Object> graph) {
        Checks.id(collectionId);
        StringBuilder out=new StringBuilder("// Neo4j import for one collection. Does not delete existing graph data.\n");
        for(Object raw:Json.list(graph, "nodes")) {
            Map<String, Object> node=Json.object(raw);
            out.append("MERGE (n:GraphRagEntity {collection: ").append(quote(collectionId)).append(", key: ").append(quote(Json.string(node, "id", ""))).append("}) SET n.name = ").append(quote(Json.string(node, "name", ""))).append(", n.kind = ").append(quote(Json.string(node, "kind", ""))).append(";\n");
        }
        for(Object raw:Json.list(graph, "edges")) {
            Map<String, Object> edge=Json.object(raw);
            out.append("MATCH (a:GraphRagEntity {collection: ").append(quote(collectionId)).append(", key: ").append(quote(Json.string(edge, "source", ""))).append("}), (b:GraphRagEntity {collection: ").append(quote(collectionId)).append(", key: ").append(quote(Json.string(edge, "target", ""))).append("}) MERGE (a)-[r:RELATED {key: ").append(quote(Json.string(edge, "id", ""))).append("}]->(b) SET r.type = ").append(quote(Json.string(edge, "type", ""))).append(", r.provenanceJson = ").append(quote(Json.write(edge.get("sources")))).append(";\n");
        }
        return out.toString();
    }
    private static String quote(String value) {
        return "'"+value.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")+"'";
    }
}
