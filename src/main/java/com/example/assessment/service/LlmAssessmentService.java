package com.example.assessment.service;

import com.example.ats.extractor.JDTextExtractor;
import com.example.assessment.dto.AssessmentDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class LlmAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(LlmAssessmentService.class);
    private static final String OPENAI_CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MODEL = "gpt-4o-mini";

    private final JDTextExtractor jdExtractor;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${openai.api-key:}")
    private String openaiApiKey;

    public LlmAssessmentService(JDTextExtractor jdExtractor, RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.jdExtractor = jdExtractor;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Generates an assessment (10 MCQs + 3 coding questions) using OpenAI based on resume and JD.
     */
    public AssessmentDto generateAssessment(String resumeText) {
        if (openaiApiKey == null || openaiApiKey.isBlank()) {
            log.warn("OPENAI_API_KEY is not set; cannot generate assessment");
            throw new IllegalStateException("OPENAI_API_KEY environment variable is required for assessment generation");
        }

        String jdText = jdExtractor.getJDText();
        String prompt = buildPrompt(jdText, resumeText);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(openaiApiKey);

        // max_tokens needed so full assessment JSON (10 MCQs + 3 coding) is not truncated
        Map<String, Object> body = Map.of(
                "model", MODEL,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "response_format", Map.of("type", "json_object"),
                "temperature", 0.3,
                "max_tokens", 4096
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(OPENAI_CHAT_URL, request, String.class);
            String responseBody = response.getBody();
            if (responseBody == null || responseBody.isBlank()) {
                throw new IllegalStateException("OpenAI response body was null or empty");
            }

            String content = extractContentFromResponse(responseBody);
            return parseAssessmentJson(content);
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("OpenAI API error {}: {}", e.getStatusCode(), errorBody != null ? errorBody : e.getMessage());
            throw new RuntimeException("OpenAI API error: " + (errorBody != null && !errorBody.isBlank() ? errorBody : e.getMessage()), e);
        } catch (RestClientException e) {
            log.error("OpenAI API call failed: {}", e.getMessage());
            throw new RuntimeException("Failed to generate assessment from OpenAI", e);
        }
    }

    private String buildPrompt(String jdText, String resumeText) {
        return """
            You are an expert technical recruiter. Generate a technical assessment based on the job description and the candidate's resume.

            Job Description:
            ---
            %s
            ---

            Candidate Resume (excerpt):
            ---
            %s
            ---

            Generate STRICT JSON only, no markdown or extra text, in this exact format:
            {
              "title": "Java Developer Assessment",
              "mcqs": [
                {
                  "question": "What is JVM?",
                  "options": ["Option A text", "Option B text", "Option C text", "Option D text"],
                  "correctAnswer": "Option B text"
                }
              ],
              "coding": [
                {
                  "title": "Two Sum Problem",
                  "difficulty": "Easy",
                  "description": "Given an array of integers..."
                }
              ]
            }

            Requirements:
            - Exactly 10 multiple-choice questions (mcqs). Each must have 4 options and correctAnswer must be one of the option strings exactly.
            - Exactly 3 coding questions: 2 Easy, 1 Medium. Each has title, difficulty ("Easy" or "Medium"), and description.
            - Align questions with the job description and technologies mentioned in the resume.
            - Output only valid JSON.
            """.formatted(
                jdText.length() > 8000 ? jdText.substring(0, 8000) + "..." : jdText,
                resumeText.length() > 6000 ? resumeText.substring(0, 6000) + "..." : resumeText
            );
    }

    private String extractContentFromResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            if (choices.isEmpty() || !choices.isArray()) {
                throw new IllegalStateException("OpenAI response had no choices");
            }
            JsonNode message = choices.get(0).path("message");
            if (message.isMissingNode()) {
                throw new IllegalStateException("OpenAI response message was null");
            }
            String content = message.path("content").asText(null);
            if (content == null || content.isBlank()) {
                throw new IllegalStateException("OpenAI response content was empty");
            }
            content = content.trim();
            if (content.startsWith("```json")) {
                content = content.substring(7).trim();
            }
            if (content.startsWith("```")) {
                content = content.substring(3).trim();
            }
            if (content.endsWith("```")) {
                content = content.substring(0, content.length() - 3).trim();
            }
            return content;
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            int len = responseBody != null ? responseBody.length() : 0;
            String snippet = responseBody != null && responseBody.length() > 200
                    ? responseBody.substring(0, 200) + "..."
                    : responseBody;
            log.error("Failed to parse OpenAI response (body length={}): {} - snippet: {}", len, e.getMessage(), snippet);
            throw new RuntimeException("Failed to parse OpenAI response", e);
        }
    }

    private AssessmentDto parseAssessmentJson(String json) {
        try {
            return objectMapper.readValue(json, AssessmentDto.class);
        } catch (Exception e) {
            int len = json != null ? json.length() : 0;
            String snippet = json != null && json.length() > 100 ? json.substring(0, 100) + "..." : json;
            log.error("Failed to parse assessment JSON (length={}): {} - snippet: {}", len, e.getMessage(), snippet);
            throw new RuntimeException("Failed to parse assessment response from OpenAI", e);
        }
    }
}
