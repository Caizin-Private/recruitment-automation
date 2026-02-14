package com.example.assessment.service;

import com.example.assessment.dto.AssessmentDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class GoogleFormService {

    private static final Logger log = LoggerFactory.getLogger(GoogleFormService.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.apps.script.url:}")
    private String appsScriptUrl;

    public GoogleFormService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a Google Form via Apps Script Web App and returns the published form URL.
     */
    public String createForm(AssessmentDto assessment) {
        if (appsScriptUrl == null || appsScriptUrl.isBlank()) {
            log.warn("Google Apps Script URL is not set (google.apps.script.url or GOOGLE_APPS_SCRIPT_URL)");
            throw new IllegalStateException("Google Apps Script URL is required for form creation");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            String jsonBody = objectMapper.writeValueAsString(assessment);
            HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(appsScriptUrl, request, Map.class);

            Map<String, Object> body = response.getBody();
            if (body == null) {
                throw new IllegalStateException("Apps Script response body was null");
            }

            Object formUrlObj = body.get("formUrl");
            if (formUrlObj == null) {
                throw new IllegalStateException("Apps Script response did not contain formUrl");
            }
            String formUrl = formUrlObj.toString();
            log.info("Google Form created: {}", formUrl);
            return formUrl;
        } catch (RestClientException e) {
            log.error("Failed to create Google Form via Apps Script: {}", e.getMessage());
            throw new RuntimeException("Failed to create Google Form", e);
        } catch (Exception e) {
            log.error("Error building request or parsing response: {}", e.getMessage());
            throw new RuntimeException("Failed to create Google Form", e);
        }
    }
}
