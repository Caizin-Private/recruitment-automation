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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class ResumeProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ResumeProcessingService.class);
    private static final String STATUS_PARSED = "PARSED";
    private static final String STATUS_ASSESSMENT_SENT = "ASSESSMENT_SENT";
    private static final String STATUS_SCREENED_REJECTED = "SCREENED_REJECTED";

    private final ResumeTextExtractor extractor;
    private final ResumeParser resumeParser;
    private final JDParser jdParser;
    private final JDTextExtractor jdExtractor;
    private final ATSScoringService atsScoringService;

    private final S3StorageService s3Service;
    private final CandidateDynamoRepository repository;

    private final LlmAssessmentService llmAssessmentService;
    private final GoogleFormService googleFormService;
    private final AssessmentEmailService assessmentEmailService;

    @Value("${recruitment.ats.threshold:60}")
    private double atsThreshold;

    public ResumeProcessingService(
            ResumeTextExtractor extractor,
            ResumeParser resumeParser,
            JDParser jdParser,
            JDTextExtractor jdExtractor,
            ATSScoringService atsScoringService,
            S3StorageService s3Service,
            CandidateDynamoRepository repository,
            LlmAssessmentService llmAssessmentService,
            GoogleFormService googleFormService,
            AssessmentEmailService assessmentEmailService
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
        this.assessmentEmailService = assessmentEmailService;
    }

    public void process(File file, String senderName, String senderEmail) {
        process(file, senderName, senderEmail, null);
    }

    public void process(
            File file,
            String senderName,
            String senderEmail,
            OAuth2AuthenticationToken authentication
    ) {
        try {
            String resumeText = extractor.extractText(file);
            ParsedResume parsedResume = resumeParser.parse(resumeText);

            String email = parsedResume.email().equals("unknown@email.com")
                    ? senderEmail
                    : parsedResume.email();

            String fullName = (parsedResume.fullName() == null
                    || parsedResume.fullName().isBlank()
                    || parsedResume.fullName().equalsIgnoreCase("UNKNOWN"))
                    ? senderName
                    : parsedResume.fullName();

            if (repository.existsByEmail(email)) {
                log.info("Candidate already exists: {}", email);
                return;
            }

            S3UploadResult uploadResult = s3Service.upload(file, "EMAIL");
            String jdText = jdExtractor.getJDText();
            JDRequirements jdRequirements = jdParser.parse(jdText);

            double atsScore = atsScoringService.calculate(
                    resumeText, jdText, parsedResume, jdRequirements);

            Candidate candidate = Candidate.create(
                    fullName,
                    email,
                    uploadResult.getBucket(),
                    uploadResult.getObjectKey(),
                    "EMAIL",
                    STATUS_PARSED);
            candidate.setAtsScore(atsScore);

            repository.save(candidate);
            log.info("Candidate saved successfully. ATS Score: {}", atsScore);

            Double score = candidate.getAtsScore();
            if (score == null || score < atsThreshold) {
                candidate.setStatus(STATUS_SCREENED_REJECTED);
                repository.save(candidate);
                log.info("ATS score {} below threshold {}; status set to {}", score, atsThreshold, STATUS_SCREENED_REJECTED);
                return;
            }

            if (STATUS_ASSESSMENT_SENT.equals(candidate.getStatus())) {
                log.info("Assessment already sent for candidate {}; skipping (idempotent)", candidate.getCandidateId());
                return;
            }

            AssessmentDto assessment;
            String formUrl;
            try {
                assessment = llmAssessmentService.generateAssessment(resumeText);
                formUrl = googleFormService.createForm(assessment);
            } catch (Exception e) {
                log.error("Assessment/form creation failed for candidate {}: {}", candidate.getCandidateId(), e.getMessage());
                throw new RuntimeException("Failed to generate assessment or form", e);
            }

            candidate.setFormUrl(formUrl);
            candidate.setStatus(STATUS_ASSESSMENT_SENT);
            repository.save(candidate);
            log.info("Candidate {} updated with formUrl and status {}", candidate.getCandidateId(), STATUS_ASSESSMENT_SENT);

            if (authentication != null) {
                try {
                    assessmentEmailService.sendAssessmentEmail(
                            candidate.getEmail(),
                            candidate.getFullName(),
                            formUrl,
                            authentication);
                } catch (Exception e) {
                    log.warn("Assessment email not sent to {} (formUrl saved in DB): {}. Add Mail.Send scope and re-consent if needed.", candidate.getEmail(), e.getMessage());
                }
            } else {
                log.debug("No authentication provided; assessment email not sent");
            }

        } catch (Exception e) {
            log.error("Error processing resume: {}", e.getMessage(), e);
            throw e;
        }
    }
}
