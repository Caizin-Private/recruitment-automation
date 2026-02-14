package com.example.assessment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CodingItem(
        String title,
        String difficulty,
        String description
) {}
