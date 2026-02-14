package com.example.ats.scorer;

import org.springframework.stereotype.Component;

@Component
public class ExperienceScorer {

    public double score(
            double candidateYears,
            double requiredYears){

        if(requiredYears <= 0)
            return 70;

        double score =
                (candidateYears / requiredYears) * 100;

        return Math.min(score, 100);
    }
}