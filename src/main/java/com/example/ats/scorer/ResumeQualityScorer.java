package com.example.ats.scorer;

import org.springframework.stereotype.Component;

@Component
public class ResumeQualityScorer {

    public double score(int wordCount){

        if(wordCount>800) return 100;

        if(wordCount>400) return 80;

        if(wordCount>200) return 60;

        return 40;
    }
}