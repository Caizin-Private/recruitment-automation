package com.example.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "candidates")
@Data
@NoArgsConstructor
public class Candidate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String fullName;
    
    @Column(nullable = false)
    private String email;

    @Column(name = "resume_s3_key", nullable = false)
    private String resumeS3Key;

    @Column(name = "resume_s3_bucket", nullable = false)
    private String resumeS3Bucket;

    @Enumerated(EnumType.STRING)
    private SourceType source;

    @Enumerated(EnumType.STRING)
    private CandidateStatus status;

    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        if (this.status == null) this.status = CandidateStatus.RECEIVED;
    }

    public enum SourceType { EMAIL, MANUAL, API }
    public enum CandidateStatus { RECEIVED, PARSED, SHORTLISTED, REJECTED }
}