package com.example.ats.model;

import java.util.List;

public record ParsedResume(

        String fullName,
        String email,

        List<String> skills,

        double yearsOfExperience,

        List<String> projects,

        String education,

        int wordCount
) {}