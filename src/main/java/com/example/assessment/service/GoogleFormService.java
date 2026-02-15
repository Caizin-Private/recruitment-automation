package com.example.assessment.service;

import com.example.assessment.dto.AssessmentDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class GoogleFormService {

    private static final Logger log = LoggerFactory.getLogger(GoogleFormService.class);

    private final RestTemplate appsScriptRestTemplate;
    private final ObjectMapper objectMapper;

    @Value("${google.apps.script.url:}")
    private String appsScriptUrl;

    public GoogleFormService(
            @Qualifier("appsScriptRestTemplate") RestTemplate appsScriptRestTemplate,
            ObjectMapper objectMapper) {
        this.appsScriptRestTemplate = appsScriptRestTemplate;
        this.objectMapper = objectMapper;
    }

    public String createForm(AssessmentDto assessment) {
        if (appsScriptUrl == null || appsScriptUrl.isBlank()) {
            log.warn("Google Apps Script URL is not set (google.apps.script.url or GOOGLE_APPS_SCRIPT_URL)");
            throw new IllegalStateException("Google Apps Script URL is required for form creation");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String jsonBody;
        try {
            jsonBody = objectMapper.writeValueAsString(assessment);
        } catch (Exception e) {
            log.error("Failed to serialize assessment to JSON: {}", e.getMessage());
            throw new RuntimeException("Failed to serialize assessment for Google Form", e);
        }

        HttpEntity<String> request = new HttpEntity<>(jsonBody, headers);
        String responseBody;

        try {
            ResponseEntity<String> response = appsScriptRestTemplate.exchange(
                    appsScriptUrl,
                    HttpMethod.POST,
                    request,
                    String.class);

            HttpStatusCode status = response.getStatusCode();
            int code = status.value();

            if (code >= 200 && code < 300) {
                responseBody = response.getBody();
                if (responseBody == null || responseBody.isBlank()) {
                    log.error("Apps Script returned 2xx but empty body");
                    throw new IllegalStateException("Apps Script response body was empty");
                }
            } else if (code >= 300 && code < 400) {
                String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                if (location == null || location.isBlank()) {
                    log.error("Apps Script returned {} but no Location header", code);
                    throw new IllegalStateException("Apps Script redirect had no Location header");
                }
                log.debug("Following Apps Script redirect to: {}", location);
                ResponseEntity<String> redirectResponse = appsScriptRestTemplate.getForEntity(location, String.class);
                responseBody = redirectResponse.getBody();
                if (responseBody == null || responseBody.isBlank()) {
                    log.error("Apps Script redirect target returned empty body");
                    throw new IllegalStateException("Apps Script redirect response body was empty");
                }
            } else {
                log.error("Apps Script returned unexpected status {}: {}", code, response.getBody());
                throw new IllegalStateException("Apps Script returned status " + code);
            }
        } catch (HttpClientErrorException | HttpServerErrorException e) {
            String errorBody = e.getResponseBodyAsString();
            log.error("Apps Script HTTP error {}: {}", e.getStatusCode(), errorBody != null ? errorBody : e.getMessage());
            throw new RuntimeException("Google Apps Script request failed: " + e.getStatusCode(), e);
        } catch (RestClientException e) {
            log.error("Apps Script request failed: {}", e.getMessage());
            throw new RuntimeException("Failed to create Google Form", e);
        }

        return extractFormUrl(responseBody);
    }

    private String extractFormUrl(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode formUrlNode = root.path("formUrl");
            if (formUrlNode.isMissingNode() || !formUrlNode.isTextual()) {
                log.error("Apps Script response missing or invalid formUrl; body length={}", responseBody.length());
                throw new IllegalStateException("Apps Script response did not contain valid formUrl");
            }
            String formUrl = formUrlNode.asText(null);
            if (formUrl == null || formUrl.isBlank()) {
                log.error("Apps Script formUrl was blank");
                throw new IllegalStateException("Apps Script formUrl was empty");
            }
            log.info("Google Form created: {}", formUrl);
            return formUrl;
        } catch (Exception e) {
            if (e instanceof IllegalStateException) {
                throw (IllegalStateException) e;
            }
            log.error("Failed to parse Apps Script JSON (length={}): {}", responseBody != null ? responseBody.length() : 0, e.getMessage());
            throw new RuntimeException("Failed to parse Google Form response", e);
        }
    }
}
