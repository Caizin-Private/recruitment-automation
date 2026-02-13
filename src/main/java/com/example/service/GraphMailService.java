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
import java.time.OffsetDateTime;

@Service
public class GraphMailService {

    private final OAuth2AuthorizedClientService clientService;
    private final ResumeProcessingService resumeProcessingService;

    // IMPORTANT: absolute path
    private static final String RESUME_DIR =
            "/Users/nikhilnegi/Desktop/recruitment-automation/resumes";

    public GraphMailService(
            OAuth2AuthorizedClientService clientService,
            ResumeProcessingService resumeProcessingService
    ) {
        this.clientService = clientService;
        this.resumeProcessingService = resumeProcessingService;
    }

    public void fetchUnreadEmailsAndDownloadResumes(
            OAuth2AuthenticationToken authentication) {

        try {

            System.out.println("Working directory: "
                    + System.getProperty("user.dir"));

            // STEP 1: Load OAuth client
            OAuth2AuthorizedClient client =
                    clientService.loadAuthorizedClient(
                            authentication.getAuthorizedClientRegistrationId(),
                            authentication.getName()
                    );

            if (client == null) {
                System.out.println("OAuth client is NULL");
                return;
            }

            String accessToken =
                    client.getAccessToken().getTokenValue();

            // STEP 2: Create credential
            TokenCredential credential = request ->
                    Mono.just(new AccessToken(
                            accessToken,
                            OffsetDateTime.now().plusHours(1)
                    ));

            // STEP 3: Create Graph client
            GraphServiceClient graphClient =
                    new GraphServiceClient(credential);

            // STEP 4: Ensure folder exists
            File folder = new File(RESUME_DIR);

            if (!folder.exists()) {

                boolean created = folder.mkdirs();

                System.out.println("Folder created: " + created);
            }

            System.out.println("Saving resumes to: "
                    + folder.getAbsolutePath());

            // STEP 5: Fetch unread emails
            var messages = graphClient
                    .me()
                    .messages()
                    .get(config -> {

                        config.queryParameters.filter =
                                "isRead eq false";

                        config.queryParameters.top = 50;
                    });

            if (messages == null ||
                    messages.getValue() == null) {

                System.out.println("No unread emails");

                return;
            }

            // STEP 6: Process each email
            for (Message message : messages.getValue()) {

                String messageId = message.getId();

                System.out.println("\nChecking email: "
                        + message.getSubject());

                if (messageId == null)
                    continue;

                if (message.getHasAttachments() == null
                        || !message.getHasAttachments()) {

                    System.out.println("No attachments");

                    continue;
                }

                var attachments = graphClient
                        .me()
                        .messages()
                        .byMessageId(messageId)
                        .attachments()
                        .get();

                if (attachments == null
                        || attachments.getValue() == null)
                    continue;

                boolean resumeDownloaded = false;

                // STEP 7: Download PDF attachments
                for (Attachment attachment :
                        attachments.getValue()) {

                    if (!(attachment instanceof
                            FileAttachment fileAttachment))
                        continue;

                    String fileName =
                            fileAttachment.getName();

                    System.out.println("Found attachment: "
                            + fileName);

                    if (fileName == null ||
                            !fileName.toLowerCase()
                                    .endsWith(".pdf")) {

                        System.out.println("Skipping non-PDF");

                        continue;
                    }

                    byte[] content =
                            fileAttachment.getContentBytes();

                    if (content == null) {

                        System.out.println(
                                "Content NULL, refetching...");

                        Attachment full =
                                graphClient
                                        .me()
                                        .messages()
                                        .byMessageId(messageId)
                                        .attachments()
                                        .byAttachmentId(
                                                attachment.getId())
                                        .get();

                        if (full instanceof
                                FileAttachment fullFile) {

                            content =
                                    fullFile.getContentBytes();
                        }
                    }

                    if (content == null) {

                        System.out.println(
                                "Failed to fetch content");

                        continue;
                    }

                    // STEP 8: Save file
                    File file =
                            new File(folder, fileName);

                    try (FileOutputStream fos =
                                 new FileOutputStream(file)) {

                        fos.write(content);

                        fos.flush();
                    }

                    // STEP 9: Verify file saved
                    if (file.exists()) {

                        System.out.println(
                                "SUCCESS: File saved");

                        System.out.println(
                                "Location: "
                                        + file.getAbsolutePath());

                        System.out.println(
                                "Size: "
                                        + file.length()
                                        + " bytes");
                    }
                    else {

                        System.out.println(
                                "ERROR: File not saved");
                    }

                    // STEP 10: Process resume
                    resumeProcessingService.process(file);

                    resumeDownloaded = true;
                }

                // STEP 11: Mark email as READ
                if (resumeDownloaded) {

                    Message update = new Message();

                    update.setIsRead(true);

                    graphClient
                            .me()
                            .messages()
                            .byMessageId(messageId)
                            .patch(update);

                    System.out.println(
                            "Email marked as READ");
                }
            }

        }
        catch (Exception e) {

            System.out.println("ERROR:");

            e.printStackTrace();
        }
    }
}