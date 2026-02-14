package com.example.ats.scorer;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class CosineSimilarityCalculator {

    public double calculate(String text1, String text2){

        Map<String, Double> v1 = tfidf(text1, text1, text2);
        Map<String, Double> v2 = tfidf(text2, text1, text2);

        return cosine(v1, v2);
    }

    private Map<String, Double> tfidf(
            String doc,
            String doc1,
            String doc2){

        List<String> words =
                tokenize(doc);

        Map<String, Double> tf =
                computeTF(words);

        Map<String, Double> idf =
                computeIDF(List.of(
                        tokenize(doc1),
                        tokenize(doc2)));

        Map<String, Double> result =
                new HashMap<>();

        for(String word: tf.keySet()){

            result.put(
                    word,
                    tf.get(word) *
                            idf.getOrDefault(word,0.0)
            );
        }

        return result;
    }

    private List<String> tokenize(String text){

        return Arrays.stream(
                        text.toLowerCase()
                                .replaceAll("[^a-z0-9 ]","")
                                .split("\\s+"))
                .filter(w->w.length()>2)
                .toList();
    }

    private Map<String, Double> computeTF(List<String> words){

        Map<String, Double> map =
                new HashMap<>();

        for(String w: words){

            map.put(w,
                    map.getOrDefault(w,0.0)+1);
        }

        int total = words.size();

        map.replaceAll((k,v)->v/total);

        return map;
    }

    private Map<String, Double> computeIDF(
            List<List<String>> docs){

        Map<String, Double> map =
                new HashMap<>();

        Set<String> words =
                new HashSet<>();

        docs.forEach(words::addAll);

        for(String word: words){

            int count=0;

            for(List<String> doc:docs)
                if(doc.contains(word)) count++;

            map.put(word,
                    Math.log((double)docs.size()/(1+count)));
        }

        return map;
    }

    private double cosine(
            Map<String,Double> v1,
            Map<String,Double> v2){

        Set<String> words =
                new HashSet<>();

        words.addAll(v1.keySet());
        words.addAll(v2.keySet());

        double dot=0,n1=0,n2=0;

        for(String w:words){

            double a=v1.getOrDefault(w,0.0);
            double b=v2.getOrDefault(w,0.0);

            dot+=a*b;
            n1+=a*a;
            n2+=b*b;
        }

        if(n1==0||n2==0)
            return 0;

        return dot/
                (Math.sqrt(n1)*Math.sqrt(n2));
    }
}