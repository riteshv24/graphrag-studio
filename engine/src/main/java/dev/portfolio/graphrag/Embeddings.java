package dev.portfolio.graphrag;

import dev.portfolio.foundation.*;
import java.util.*;

/** Two explicit vector spaces: a deterministic lexical hash baseline, or real Ollama embeddings.
 * Hash vectors are not described as semantic embeddings. Provider/model/dimension signatures prevent mixing.
 */
public final class Embeddings {
    public record Vector(List<Double> values, String signature) {
    }
    private final ModelClient model;
    public Embeddings(ModelClient model) {
        this.model=model;
    }
    public Vector embed(String text, String provider) throws Exception {
        if(provider.equals("ollama")) {
            List<Double> vector=normalize(model.embed(text));
            return new Vector(vector, "ollama:"+model.embeddingModel()+":"+vector.size());
        }
        if(!provider.equals("hash"))throw Problem.invalid("Unknown embedding provider");
        return new Vector(hash(text, 256), "lexical-hash-v1:256");
    }
    public static List<Double> hash(String text, int dimensions) {
        Checks.range(dimensions, "dimensions", 8, 8192);
        double[] values=new double[dimensions];
        for(String term:Text.terms(text)) {
            add(values, "t:"+term, 1.0);
            if(term.length()>=4)for(int i=0; i<=term.length()-3; i++)add(values, "g:"+term.substring(i, i+3), .20);
        }
        return normalize(Arrays.stream(values).boxed().toList());
    }
    private static void add(double[] vector, String feature, double weight) {
        int hash=feature.hashCode();
        hash^=(hash>>>16);
        int slot=Math.floorMod(hash, vector.length);
        vector[slot]+=((hash&0x40000000)==0?1:-1)*weight;
    }
    public static List<Double> normalize(List<Double> values) {
        double length=Math.sqrt(values.stream().mapToDouble(v->v*v).sum());
        if(length==0)return values;
        return values.stream().map(v->v/length).toList();
    }
    public static double cosine(List<Double> a, List<Double> b) {
        if(a.size()!=b.size())throw Problem.conflict("Vector dimensions differ; reindex the collection with one embedding configuration");
        double dot=0, left=0, right=0;
        for(int i=0; i<a.size(); i++) {
            dot+=a.get(i)*b.get(i);
            left+=a.get(i)*a.get(i);
            right+=b.get(i)*b.get(i);
        }
        return left==0||right==0?0:Math.max(-1, Math.min(1, dot/Math.sqrt(left*right)));
    }
    public static List<Double> values(Map<String, Object> chunk) {
        return Json.list(chunk, "vector").stream().map(v->((Number)v).doubleValue()).toList();
    }
}
