package com.example.service;

import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.serviceclient.GraphServiceClient;
import com.microsoft.graph.users.item.sendmail.SendMailPostRequestBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;

import reactor.core.publisher.Mono;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;

import java.time.OffsetDateTime;
import java.util.List;

@Service
public class AssessmentEmailService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentEmailService.class);

    @Value("${recruitment.assessment.email.subject:Complete your assessment}")
    private String defaultSubject;

    @Value("${recruitment.assessment.email.from-name:Recruitment Team}")
    private String fromName;

    private final OAuth2AuthorizedClientService clientService;

    public AssessmentEmailService(OAuth2AuthorizedClientService clientService) {
        this.clientService = clientService;
    }

    public void sendAssessmentEmail(String toEmail, String toName, String formUrl, OAuth2AuthenticationToken authentication) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Cannot send assessment email: recipient email is blank");
            throw new IllegalArgumentException("Recipient email is required");
        }
        if (formUrl == null || formUrl.isBlank()) {
            log.warn("Cannot send assessment email: form URL is blank");
            throw new IllegalArgumentException("Form URL is required");
        }
        if (authentication == null) {
            log.warn("Cannot send assessment email: no authentication (missing OAuth2 token)");
            throw new IllegalArgumentException("Authentication is required to send email via Microsoft Graph");
        }

        var client = clientService.loadAuthorizedClient(
                authentication.getAuthorizedClientRegistrationId(),
                authentication.getName());
        if (client == null) {
            log.error("No OAuth2 authorized client found for user");
            throw new IllegalStateException("No OAuth2 client available to send email");
        }

        String accessToken = client.getAccessToken().getTokenValue();
        TokenCredential credential = request ->
                Mono.just(new AccessToken(accessToken, OffsetDateTime.now().plusHours(1)));

        GraphServiceClient graphClient = new GraphServiceClient(credential);

        Message message = new Message();
        message.setSubject(defaultSubject);

        ItemBody body = new ItemBody();
        body.setContentType(com.microsoft.graph.models.BodyType.Html);
        body.setContent(buildHtmlBody(toName, formUrl));
        message.setBody(body);

        Recipient recipient = new Recipient();
        EmailAddress emailAddress = new EmailAddress();
        emailAddress.setAddress(toEmail);
        if (toName != null && !toName.isBlank()) {
            emailAddress.setName(toName);
        }
        recipient.setEmailAddress(emailAddress);
        message.setToRecipients(List.of(recipient));

        try {
            SendMailPostRequestBody requestBody = new SendMailPostRequestBody();
            requestBody.setMessage(message);
            requestBody.setSaveToSentItems(true);
            graphClient.me()
                    .sendMail()
                    .post(requestBody);
            log.info("Assessment email sent to {}", toEmail);
        } catch (Exception e) {
            log.error("Failed to send assessment email to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send assessment email", e);
        }
    }

    private static String buildHtmlBody(String candidateName, String formUrl) {
        String greeting = (candidateName != null && !candidateName.isBlank())
                ? "Hi " + candidateName + ","
                : "Hi,";
        return """
            <p>%s</p>
            <p>Please complete your technical assessment using the link below.</p>
            <p><a href="%s">Open assessment</a></p>
            <p>If the link does not work, copy and paste this URL into your browser:</p>
            <p>%s</p>
            <p>Thanks,<br/>Recruitment Team</p>
            """.formatted(greeting, formUrl, formUrl);
    }
}
