package com.example.ats.scorer;

import org.springframework.stereotype.Component;

@Component
public class EducationScorer {

    public double score(String education){

        education = education.toLowerCase();

        if(education.contains("iit")
                || education.contains("nit"))
            return 100;

        if(education.contains("b.tech")
                || education.contains("m.tech"))
            return 80;

        return 50;
    }
}