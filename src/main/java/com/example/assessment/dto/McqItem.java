package com.example.assessment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record McqItem(
        String question,
        List<String> options,
        @JsonProperty("correctAnswer") String correctAnswer
) {}
