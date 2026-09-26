package dev.portfolio.graphrag;

import dev.portfolio.foundation.*;
import java.util.*;

public final class CollectionService {
    private final Store store;
    public CollectionService(Store store) {
        this.store=store;
    }
    public Map<String, Object> create(Map<String, Object> input) {
        String name=Checks.text(Json.string(input, "name", ""), "name", 100), description=Checks.optional(Json.string(input, "description", ""), "description", 1200);
        String provider=Checks.oneOf(Json.string(input, "embeddingProvider", "hash"), "embeddingProvider", "hash", "ollama");
        Map<String, Object> aliases=input.get("aliases") instanceof Map<?, ?>?Json.object(input.get("aliases")):Map.of();
        if(aliases.size()>100)throw Problem.invalid("At most 100 aliases are supported");
        Map<String, Object> checked=new LinkedHashMap<>();
        aliases.forEach((k, v)-> {
            if(!(v instanceof String value))throw Problem.invalid("Alias targets must be text");
            checked.put(Checks.text(k, "alias", 70), Checks.text(value, "canonical name", 70));
        });
        int size=Checks.range(Json.integer(input, "chunkSize", 900), "chunkSize", 200, 2400), overlap=Checks.range(Json.integer(input, "overlap", 120), "overlap", 0, size/2);
        return store.write(tx-> {
            if(tx.scan("collections").size()>=12)throw Problem.invalid("Workspace supports 12 collections");
            String id=Checks.uuid();
            Map<String, Object> result=Json.map("id", id, "name", name, "description", description, "embeddingProvider", provider, "aliases", checked, "chunkSize", size, "overlap", overlap, "createdAt", Checks.now());
            tx.put("collections", id, result);
            Audit.record(tx, "collection.created", id);
            return result;
        });
    }
    public Map<String, Object> get(String id) {
        return store.read(tx->tx.require("collections", Checks.id(id)));
    }
    public List<Map<String, Object>> list() {
        return store.read(tx->tx.scan("collections").stream().map(c-> {
            List<Map<String, Object>> docs=tx.scan("documents").stream().filter(d->d.get("collectionId").equals(c.get("id"))).toList();
            c.put("documentCount", docs.size());
            c.put("chunkCount", docs.stream().mapToInt(d->Json.list(d, "chunks").size()).sum());
            return c;
        }).toList());
    }
}
