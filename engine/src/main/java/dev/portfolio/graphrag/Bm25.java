package dev.portfolio.graphrag;

import dev.portfolio.foundation.*;
import java.util.*;

/** Okapi BM25 over chunk text. Corpus statistics are rebuilt from the current immutable document versions. */
public final class Bm25 {
    private final Map<String, Map<String, Integer>> frequencies=new LinkedHashMap<>();
    private final Map<String, Integer> lengths=new HashMap<>();
    private final Map<String, Integer> documentFrequency=new HashMap<>();
    private final double averageLength;
    public Bm25(Map<String, String> documents) {
        int total=0;
        for(var entry:documents.entrySet()) {
            List<String> terms=Text.terms(entry.getValue());
            Map<String, Integer> counts=Text.frequencies(terms);
            frequencies.put(entry.getKey(), counts);
            lengths.put(entry.getKey(), terms.size());
            total+=terms.size();
            counts.keySet().forEach(term->documentFrequency.merge(term, 1, Integer::sum));
        }
        averageLength=documents.isEmpty()?1:Math.max(1, (double)total/documents.size());
    }
    public Map<String, Double> scores(String query) {
        Set<String> terms=Text.uniqueTerms(query);
        Map<String, Double> results=new LinkedHashMap<>();
        int size=frequencies.size();
        frequencies.forEach((id, frequency)-> {
            double score=0;
            for(String term:terms) {
                int tf=frequency.getOrDefault(term, 0);
                if(tf==0)continue;
                int df=documentFrequency.getOrDefault(term, 0);
                double idf=Math.log(1+(size-df+.5)/(df+.5));
                double denominator=tf+1.2*(1-.75+.75*lengths.get(id)/averageLength);
                score+=idf*(tf*(1.2+1))/denominator;
            }
            if(score>0)results.put(id, score);
        });
        return results;
    }
}
