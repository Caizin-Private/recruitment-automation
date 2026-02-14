package com.example.service;

import com.example.assessment.dto.AssessmentDto;
import com.example.assessment.service.GoogleFormService;
import com.example.assessment.service.LlmAssessmentService;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class ResumeProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ResumeProcessingService.class);

    private final ResumeTextExtractor extractor;
    private final ResumeParser resumeParser;
    private final JDParser jdParser;
    private final JDTextExtractor jdExtractor;
    private final ATSScoringService atsScoringService;

    private final S3StorageService s3Service;
    private final CandidateDynamoRepository repository;

    private final LlmAssessmentService llmAssessmentService;
    private final GoogleFormService googleFormService;

    public ResumeProcessingService(
            ResumeTextExtractor extractor,
            ResumeParser resumeParser,
            JDParser jdParser,
            JDTextExtractor jdExtractor,
            ATSScoringService atsScoringService,
            S3StorageService s3Service,
            CandidateDynamoRepository repository,
            LlmAssessmentService llmAssessmentService,
            GoogleFormService googleFormService
    ) {
        this.extractor = extractor;
        this.resumeParser = resumeParser;
        this.jdParser = jdParser;
        this.jdExtractor = jdExtractor;
        this.atsScoringService = atsScoringService;
        this.s3Service = s3Service;
        this.repository = repository;
        this.llmAssessmentService = llmAssessmentService;
        this.googleFormService = googleFormService;
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
                log.info("Candidate already exists: {}", email);
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

            log.info("Candidate saved successfully. ATS Score: {}", atsScore);

//            // STEP 13: Generate assessment and create Google Form (log link only; do not send email)
//            try {
//                AssessmentDto assessment = llmAssessmentService.generateAssessment(resumeText);
//                System.out.println(assessment);
//                String formUrl = googleFormService.createForm(assessment);
//                System.out.println("Generated Google Form URL: " + formUrl);
//                log.info("Generated Assessment Form: {}", formUrl);
//            } catch (Exception ex) {
//                log.warn("Assessment/form creation skipped or failed: {}", ex.getMessage());
//            }

        } catch (Exception e) {
            log.error("Error processing resume: {}", e.getMessage(), e);
        }
    }
}