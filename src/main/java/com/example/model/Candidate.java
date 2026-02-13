package com.example.model;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@NoArgsConstructor
@DynamoDbBean
public class Candidate {

    private String candidateId;
    private String fullName;
    private String email;
    private String resumeS3Key;
    private String resumeS3Bucket;
    private String source;
    private String status;
    private String createdAt;

    // ---------- Primary Key ----------
    @DynamoDbPartitionKey
    public String getCandidateId() {
        return candidateId;
    }

    // ---------- GSI: email-index ----------
    @DynamoDbSecondaryPartitionKey(indexNames = "email-index")
    public String getEmail() {
        return email;
    }

    public static Candidate create(
            String fullName,
            String email,
            String bucket,
            String key,
            String source,
            String status
    ) {
        Candidate c = new Candidate();
        c.candidateId = UUID.randomUUID().toString();
        c.fullName = fullName;
        c.email = email;
        c.resumeS3Bucket = bucket;
        c.resumeS3Key = key;
        c.source = source;
        c.status = status;
        c.createdAt = Instant.now().toString();
        return c;
    }
}
