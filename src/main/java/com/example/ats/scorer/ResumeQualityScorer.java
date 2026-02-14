package com.example.ats.scorer;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ResumeQualityScorer {

    public double score(
            String resumeText,
            List<String> skills,
            double experienceYears,
            List<String> projects) {

        double lengthScore = lengthScore(resumeText);

        double skillsScore = skillsScore(skills);

        double experienceScore = experienceScore(experienceYears);

        double projectScore = projectScore(projects);

        double keywordScore = keywordScore(resumeText);

        double finalScore =
                0.30 * lengthScore +
                        0.25 * skillsScore +
                        0.20 * experienceScore +
                        0.15 * projectScore +
                        0.10 * keywordScore;

        return Math.min(finalScore, 100);
    }

    private double lengthScore(String text) {

        int words = text.split("\\s+").length;

        if (words > 800) return 100;
        if (words > 500) return 85;
        if (words > 300) return 70;
        if (words > 150) return 50;

        return 30;
    }

    private double skillsScore(List<String> skills) {

        if (skills.size() >= 8) return 100;
        if (skills.size() >= 5) return 80;
        if (skills.size() >= 3) return 60;

        return 40;
    }

    private double experienceScore(double years) {

        if (years >= 5) return 100;
        if (years >= 3) return 80;
        if (years >= 1) return 60;

        return 40;
    }

    private double projectScore(List<String> projects) {

        if (projects.size() >= 4) return 100;
        if (projects.size() >= 2) return 80;
        if (projects.size() >= 1) return 60;

        return 40;
    }

    private double keywordScore(String text) {

        String lower = text.toLowerCase();

        int score = 0;

        if (lower.contains("experience")) score += 20;
        if (lower.contains("project")) score += 20;
        if (lower.contains("skills")) score += 20;
        if (lower.contains("developed")) score += 20;
        if (lower.contains("implemented")) score += 20;

        return score;
    }
}