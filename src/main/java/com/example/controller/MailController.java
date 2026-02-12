package com.example.controller;

import com.example.service.GraphMailService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MailController {

    private final GraphMailService service;

    public MailController(GraphMailService service) {
        this.service = service;
    }

    @GetMapping("/emails")
    public String getEmails(OAuth2AuthenticationToken authentication) {
        service.fetchEmails(authentication);
        return "Check console for emails";
    }
}