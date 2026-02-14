package com.example.ats.scorer;

import com.example.ats.model.ParsedResume;
import com.example.ats.model.JDRequirements;
import org.springframework.stereotype.Service;

@Service
public class ATSScoringService {

    private final SkillSimilarityScorer skill;
    private final ExperienceScorer experience;
    private final ProjectScorer project;
    private final EducationScorer education;
    private final ResumeQualityScorer quality;

    public ATSScoringService(
            SkillSimilarityScorer skill,
            ExperienceScorer experience,
            ProjectScorer project,
            EducationScorer education,
            ResumeQualityScorer quality){

        this.skill=skill;
        this.experience=experience;
        this.project=project;
        this.education=education;
        this.quality=quality;
    }

    public double calculate(
            String resumeText,
            String jdText,
            ParsedResume parsed,
            JDRequirements jdReq){

        double skillScore =
                skill.score(resumeText,jdText);

        double experienceScore =
                experience.score(
                        parsed.yearsOfExperience(),
                        jdReq.requiredExperienceYears());

        double projectScore =
                project.score(parsed.projects());

        double educationScore =
                education.score(parsed.education());

        double qualityScore =
                quality.score(parsed.wordCount());

        double finalScore =
                0.40*skillScore +
                        0.20*experienceScore +
                        0.15*projectScore +
                        0.15*educationScore +
                        0.10*qualityScore;

        return Math.round(finalScore*100.0)/100.0;
    }
}