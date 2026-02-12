package com.example.service;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.serviceclient.GraphServiceClient;

import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.time.OffsetDateTime;

@Service
public class GraphMailService {

    private final OAuth2AuthorizedClientService clientService;

    public GraphMailService(OAuth2AuthorizedClientService clientService) {
        this.clientService = clientService;
    }

    public void fetchEmailsAndDownloadResumes(OAuth2AuthenticationToken authentication) {

        try {

            // 1. Get authorized client
            OAuth2AuthorizedClient client =
                    clientService.loadAuthorizedClient(
                            authentication.getAuthorizedClientRegistrationId(),
                            authentication.getName()
                    );

            if (client == null) {
                System.out.println("Client is null");
                return;
            }

            String accessToken = client.getAccessToken().getTokenValue();

            // 2. Create TokenCredential (CORRECT for SDK v6)
            TokenCredential credential = new TokenCredential() {
                @Override
                public Mono<AccessToken> getToken(TokenRequestContext request) {

                    return Mono.just(
                            new AccessToken(
                                    accessToken,
                                    OffsetDateTime.now().plusHours(1)
                            )
                    );
                }
            };

            // 3. Create Graph client
            GraphServiceClient graphClient = new GraphServiceClient(credential);

            // 4. Create resumes folder if not exists
            File folder = new File("resumes");
            if (!folder.exists()) {
                folder.mkdir();
            }

            // 5. Fetch emails
            var messages = graphClient
                    .me()
                    .messages()
                    .get();

            if (messages == null || messages.getValue() == null) {
                System.out.println("No messages found");
                return;
            }

            // 6. Loop emails
            for (Message message : messages.getValue()) {

                System.out.println("Checking email: " + message.getSubject());

                Boolean hasAttachments = message.getHasAttachments();

                if (hasAttachments == null || !hasAttachments) {
                    continue;
                }

                String messageId = message.getId();

                if (messageId == null) {
                    continue;
                }

                // 7. Fetch attachments
                var attachments = graphClient
                        .me()
                        .messages()
                        .byMessageId(messageId)
                        .attachments()
                        .get();

                if (attachments == null || attachments.getValue() == null) {
                    continue;
                }

                // 8. Loop attachments
                for (Attachment attachment : attachments.getValue()) {

                    if (attachment instanceof FileAttachment fileAttachment) {

                        String fileName = fileAttachment.getName();

                        if (fileName == null) {
                            continue;
                        }

                        // only PDF resumes
                        if (!fileName.toLowerCase().endsWith(".pdf")) {
                            continue;
                        }

                        byte[] content = fileAttachment.getContentBytes();

                        if (content == null) {
                            continue;
                        }

                        // 9. Save file
                        File file = new File(folder, fileName);

                        OutputStream os = new FileOutputStream(file);
                        os.write(content);
                        os.close();

                        System.out.println("Downloaded resume: " + fileName);
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}