package com.example.ats.scorer;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProjectScorer {

    public double score(List<String> projects){

        if(projects.isEmpty())
            return 40;

        return Math.min(
                projects.size()*20,100);
    }
}