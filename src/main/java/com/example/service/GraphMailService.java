package com.example.service;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class GraphMailService {

    private final OAuth2AuthorizedClientService clientService;

    public GraphMailService(OAuth2AuthorizedClientService clientService) {
        this.clientService = clientService;
    }

    public void fetchEmails(OAuth2AuthenticationToken authentication) {

        // Get access token from Spring Security
        OAuth2AuthorizedClient client =
                clientService.loadAuthorizedClient(
                        authentication.getAuthorizedClientRegistrationId(),
                        authentication.getName());

        String accessToken = client.getAccessToken().getTokenValue();

        // Call Microsoft Graph REST API directly
        RestClient restClient = RestClient.builder()
                .baseUrl("https://graph.microsoft.com/v1.0")
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .build();

        Map response = restClient.get()
                .uri("/me/messages?$top=5")
                .retrieve()
                .body(Map.class);

        var emails = (java.util.List<Map>) response.get("value");

        for (Map email : emails) {
            System.out.println("Subject: " + email.get("subject"));
        }
    }
}