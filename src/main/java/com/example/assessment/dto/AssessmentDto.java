package com.example.assessment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AssessmentDto(
        String title,
        List<McqItem> mcqs,
        @JsonProperty("coding") List<CodingItem> coding
) {}
