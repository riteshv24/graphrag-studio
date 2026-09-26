package dev.portfolio.graphrag;

import dev.portfolio.foundation.*;
import java.util.*;
import java.util.regex.*;

/** Provenance-first extraction. Explicit triples are typed claims; co-occurrence is never labelled causation. */
public final class EntityExtractor {
    private static final Pattern TRIPLE=Pattern.compile("(?m)^\\s*([^\\n<>]{2,70}?)\\s*->\\s*([A-Z_]{2,24})\\s*->\\s*([^\\n<>]{2,70}?)\\s*$");
    private static final Set<String> RELATIONS=Set.of("DEPENDS_ON", "OWNS", "USES", "RUNS_ON", "STORES_IN", "SENDS_TO", "MONITORS", "SERVES", "MANAGES", "CALLS", "CO_OCCURS");
    private static final Pattern PROPER=Pattern.compile("\\b(?:[A-Z][a-z]+(?:[A-Z][a-z]+)+|[A-Z]{2,8}|[A-Z][a-z]+(?: [A-Z][a-z]+){1,3})\\b");
    public record Entity(String id, String name, String kind) {
        public Map<String, Object> json() {
            return Json.map("id", id, "name", name, "kind", kind);
        }
    }
    public record Relation(String source, String target, String type, String evidence, int offset) {
        public Map<String, Object> json() {
            return Json.map("source", source, "target", target, "type", type, "evidence", evidence, "offset", offset);
        }
    }
    public record Extraction(List<Entity> entities, List<Relation> relations) {
    }
    public Extraction extract(String text, Map<String, String> aliases) {
        Map<String, Entity> entities=new LinkedHashMap<>();
        List<Relation> relations=new ArrayList<>();
        Matcher triples=TRIPLE.matcher(text);
        while(triples.find()) {
            String type=triples.group(2);
            if(!RELATIONS.contains(type))continue;
            Entity source=entity(clean(triples.group(1)), aliases), target=entity(clean(triples.group(3)), aliases);
            entities.put(source.id, source);
            entities.put(target.id, target);
            if(!source.id.equals(target.id))relations.add(new Relation(source.id, target.id, type, triples.group().strip(), triples.start()));
        }
        for(var alias:aliases.entrySet())if(Pattern.compile("(?iu)(?<![\\p{L}\\p{N}])"+Pattern.quote(alias.getKey())+"(?![\\p{L}\\p{N}])").matcher(text).find()) {
            Entity entity=entity(alias.getValue(), aliases);
            entities.put(entity.id, entity);
        }
        Matcher names=PROPER.matcher(text);
        while(names.find()&&entities.size()<35) {
            String value=names.group();
            if(RELATIONS.contains(value)||Set.of("The", "This", "That", "API", "JSON", "CSV", "HTTP", "Service Level").contains(value))continue;
            Entity entity=entity(value, aliases);
            entities.putIfAbsent(entity.id, entity);
        }
        List<Entity> ordered=new ArrayList<>(entities.values());
        // A small co-occurrence graph is useful for exploration, but these edges are explicitly weak.
        for(int i=0; i<Math.min(ordered.size(), 8); i++)for(int j=i+1; j<Math.min(ordered.size(), 8); j++) {
            String source=ordered.get(i).id, target=ordered.get(j).id;
            boolean typed=relations.stream().anyMatch(e->e.source.equals(source)&&e.target.equals(target)||e.source.equals(target)&&e.target.equals(source));
            if(!typed)relations.add(new Relation(source, target, "CO_OCCURS", Text.excerpt(text, 220), 0));
        }
        return new Extraction(List.copyOf(entities.values()), List.copyOf(relations));
    }
    private static String clean(String value) {
        return value.strip().replaceAll("[.;]$", "").replaceAll("\\s+", " ");
    }
    public Entity entity(String name, Map<String, String> aliases) {
        String canonical=aliases.entrySet().stream().filter(e->e.getKey().equalsIgnoreCase(name)).map(Map.Entry::getValue).findFirst().orElse(name).strip();
        String kind=canonical.toLowerCase(Locale.ROOT).contains("team")?"team":canonical.toLowerCase(Locale.ROOT).contains("service")?"service":Set.of("postgresql", "redis", "kafka").contains(canonical.toLowerCase(Locale.ROOT))?"data":"concept";
        return new Entity(Checks.sha256(canonical.toLowerCase(Locale.ROOT)).substring(0, 20), canonical, kind);
    }
}
