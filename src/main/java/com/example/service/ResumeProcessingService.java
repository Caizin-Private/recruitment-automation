package com.example.service;

import com.example.ats.extractor.JDTextExtractor;
import com.example.ats.model.JDRequirements;
import com.example.ats.model.ParsedResume;
import com.example.ats.parser.JDParser;
import com.example.ats.parser.ResumeParser;
import com.example.ats.scorer.ATSScoringService;

import com.example.infrastructure.storage.S3StorageService;
import com.example.infrastructure.storage.S3UploadResult;
import com.example.model.Candidate;
import com.example.repository.CandidateDynamoRepository;

import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class ResumeProcessingService {

    private final ResumeTextExtractor extractor;
    private final ResumeParser resumeParser;
    private final JDParser jdParser;
    private final JDTextExtractor jdExtractor;
    private final ATSScoringService atsScoringService;

    private final S3StorageService s3Service;
    private final CandidateDynamoRepository repository;

    public ResumeProcessingService(
            ResumeTextExtractor extractor,
            ResumeParser resumeParser,
            JDParser jdParser,
            JDTextExtractor jdExtractor,
            ATSScoringService atsScoringService,
            S3StorageService s3Service,
            CandidateDynamoRepository repository
    ) {
        this.extractor = extractor;
        this.resumeParser = resumeParser;
        this.jdParser = jdParser;
        this.jdExtractor = jdExtractor;
        this.atsScoringService = atsScoringService;
        this.s3Service = s3Service;
        this.repository = repository;
    }

    public void process(
            File file,
            String senderName,
            String senderEmail
    ) {

        try {

            // STEP 1: Extract resume text
            String resumeText =
                    extractor.extractText(file);

            // STEP 2: Parse resume
            ParsedResume parsedResume =
                    resumeParser.parse(resumeText);

            // STEP 3: Determine email
            String email =
                    parsedResume.email().equals("unknown@email.com")
                            ? senderEmail
                            : parsedResume.email();

            // STEP 4: Determine full name
            String fullName =
                    (parsedResume.fullName() == null ||
                            parsedResume.fullName().isBlank() ||
                            parsedResume.fullName().equalsIgnoreCase("UNKNOWN"))
                            ? senderName
                            : parsedResume.fullName();

            // STEP 5: Check duplicate candidate
            if (repository.existsByEmail(email)) {

                System.out.println(
                        "Candidate already exists: " + email);

                return;
            }

            // STEP 6: Upload resume to S3
            S3UploadResult uploadResult =
                    s3Service.upload(file, "EMAIL");

            // STEP 7: Extract JD text
            String jdText =
                    jdExtractor.getJDText();

            // STEP 8: Parse JD requirements
            JDRequirements jdRequirements =
                    jdParser.parse(jdText);

            // STEP 9: Calculate ATS score (FULL MULTI-FACTOR)
            double atsScore =
                    atsScoringService.calculate(
                            resumeText,
                            jdText,
                            parsedResume,
                            jdRequirements
                    );

            // STEP 10: Create Candidate object
            Candidate candidate =
                    Candidate.create(
                            fullName,
                            email,
                            uploadResult.getBucket(),
                            uploadResult.getObjectKey(),
                            "EMAIL",
                            "PARSED"
                    );

            // STEP 11: Set ATS score
            candidate.setAtsScore(atsScore);

            // STEP 12: Save to DynamoDB
            repository.save(candidate);

            System.out.println(
                    "Candidate saved successfully. ATS Score: "
                            + atsScore);

        } catch (Exception e) {

            System.err.println(
                    "Error processing resume: " + e.getMessage());

            e.printStackTrace();
        }
    }
}