package com.example.ats.scorer;

import com.example.ats.model.*;
import org.springframework.stereotype.Service;

@Service
public class ATSScoringService {

    private final SkillSimilarityScorer skill;
    private final ExperienceScorer experience;
    private final ProjectScorer project;
    private final ResumeQualityScorer quality;

    public ATSScoringService(
            SkillSimilarityScorer skill,
            ExperienceScorer experience,
            ProjectScorer project,
            ResumeQualityScorer quality){

        this.skill=skill;
        this.experience=experience;
        this.project=project;
        this.quality=quality;
    }

    public double calculate(
            String resumeText,
            String jdText,
            ParsedResume parsed,
            JDRequirements jdReq){

        double skillScore =
                skill.score(resumeText, jdText);

        double experienceScore =
                experience.score(
                        parsed.yearsOfExperience(),
                        jdReq.requiredExperienceYears());

        double projectScore =
                project.score(parsed.projects());

        double qualityScore =
                quality.score(
                        resumeText,
                        parsed.skills(),
                        parsed.yearsOfExperience(),
                        parsed.projects()
                );

        double finalScore =
                0.45*skillScore +
                        0.25*experienceScore +
                        0.20*projectScore +
                        0.10*qualityScore;

        return Math.round(finalScore*100.0)/100.0;
    }
}