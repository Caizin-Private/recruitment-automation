package com.example.service;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenCredential;
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
    private final ResumeProcessingService resumeProcessingService;

    public GraphMailService(
            OAuth2AuthorizedClientService clientService,
            ResumeProcessingService resumeProcessingService
    ) {
        this.clientService = clientService;
        this.resumeProcessingService = resumeProcessingService;
    }

    public void fetchUnreadEmailsAndDownloadResumes(OAuth2AuthenticationToken authentication) {

        try {

            // STEP 1: Get OAuth client
            OAuth2AuthorizedClient client =
                    clientService.loadAuthorizedClient(
                            authentication.getAuthorizedClientRegistrationId(),
                            authentication.getName()
                    );

            if (client == null) {
                System.out.println("OAuth client is NULL");
                return;
            }

            String accessToken = client.getAccessToken().getTokenValue();

            // STEP 2: Create TokenCredential
            TokenCredential credential = request ->
                    Mono.just(
                            new AccessToken(
                                    accessToken,
                                    OffsetDateTime.now().plusHours(1)
                            )
                    );

            // STEP 3: Create Graph client
            GraphServiceClient graphClient = new GraphServiceClient(credential);

            // STEP 4: Ensure resumes folder exists
            File tempDir = new File("resumes");
            if (!tempDir.exists())
                tempDir.mkdirs();

            // STEP 5: Fetch only UNREAD emails
            var messages = graphClient
                    .me()
                    .messages()
                    .get(config -> {
                        config.queryParameters.filter = "isRead eq false";
                        config.queryParameters.top = 50;
                    });

            if (messages == null || messages.getValue() == null) {
                System.out.println("No unread messages found");
                return;
            }

            // STEP 6: Loop messages
            for (Message message : messages.getValue()) {

                String messageId = message.getId();
                String subject = message.getSubject();

                System.out.println("\nChecking UNREAD email: " + subject);

                if (messageId == null)
                    continue;

                if (message.getHasAttachments() == null ||
                        !message.getHasAttachments()) {

                    System.out.println("No attachments found.");
                    continue;
                }

                // STEP 7: Fetch attachments properly
                var attachments = graphClient
                        .me()
                        .messages()
                        .byMessageId(messageId)
                        .attachments()
                        .get();

                if (attachments == null || attachments.getValue() == null) {
                    System.out.println("Attachments NULL");
                    continue;
                }

                boolean resumeDownloaded = false;

                // STEP 8: Process attachments
                for (Attachment attachment : attachments.getValue()) {

                    if (!(attachment instanceof FileAttachment fileAttachment)) {
                        continue;
                    }

                    String fileName = fileAttachment.getName();

                    System.out.println("Found attachment: " + fileName);

                    if (fileName == null ||
                            !fileName.toLowerCase().endsWith(".pdf")) {

                        System.out.println("Skipping non-PDF file");
                        continue;
                    }

                    byte[] content = fileAttachment.getContentBytes();

                    // IMPORTANT: fetch content if null
                    if (content == null) {

                        System.out.println("ContentBytes NULL, fetching attachment again...");

                        Attachment fullAttachment =
                                graphClient
                                        .me()
                                        .messages()
                                        .byMessageId(messageId)
                                        .attachments()
                                        .byAttachmentId(attachment.getId())
                                        .get();

                        if (fullAttachment instanceof FileAttachment fullFileAttachment) {
                            content = fullFileAttachment.getContentBytes();
                        }
                    }

                    if (content == null) {
                        System.out.println("Still NULL content, skipping.");
                        continue;
                    }

                    // STEP 9: Write temp file (UPDATED)
                    File tempFile = new File(tempDir, fileName);
                    boolean success = false;

                    try (OutputStream os = new FileOutputStream(tempFile)) {
                        os.write(content);

                        // STEP 10: Process resume
                        String senderEmail =
                                message.getFrom()
                                        .getEmailAddress()
                                        .getAddress();

                        String senderName =
                                message.getFrom()
                                        .getEmailAddress()
                                        .getName();

                        resumeProcessingService.process(
                                tempFile,
                                senderName,
                                senderEmail
                        );
                        success = true;
                        resumeDownloaded = true;

                    } catch (Exception e) {

                        System.out.println(
                                "Resume processing failed for: " + fileName
                        );
                        e.printStackTrace();

                    } finally {
                        // STEP 11: Delete ONLY after success
                        if (success && tempFile.exists()) {
                            tempFile.delete();
                        }
                    }
                }

                // STEP 12: Mark email as READ only if resume downloaded
                if (resumeDownloaded) {

                    Message updateMessage = new Message();
                    updateMessage.setIsRead(true);

                    graphClient
                            .me()
                            .messages()
                            .byMessageId(messageId)
                            .patch(updateMessage);

                    System.out.println("Marked email as READ: " + subject);
                }
                else {
                    System.out.println("No PDF resumes found in email.");
                }
            }

        } catch (Exception e) {

            System.out.println("\nERROR while fetching emails:");
            e.printStackTrace();
        }
    }
}
