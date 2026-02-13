package com.example.service;

import com.example.dto.ParsedResume;
import com.example.infrastructure.storage.S3StorageService;
import com.example.infrastructure.storage.S3UploadResult;
import com.example.model.Candidate;
import com.example.repository.CandidateDynamoRepository;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class ResumeProcessingService {

    private final ResumeTextExtractor extractor;
    private final ResumeParserService parser;
    private final S3StorageService s3Service;
    private final CandidateDynamoRepository repository;

    public ResumeProcessingService(
            ResumeTextExtractor extractor,
            ResumeParserService parser,
            S3StorageService s3Service,
            CandidateDynamoRepository repository
    ) {
        this.extractor = extractor;
        this.parser = parser;
        this.s3Service = s3Service;
        this.repository = repository;
    }

    public void process(File file) {

        String text = extractor.extractText(file);
        ParsedResume parsed = parser.parse(text);

        if (repository.existsByEmail(parsed.email())) {
            System.out.println("Candidate already exists: " + parsed.email());
            return;
        }

        S3UploadResult uploadResult = s3Service.upload(file, "EMAIL");

        Candidate candidate = Candidate.create(
                parsed.fullName(),
                parsed.email(),
                uploadResult.getBucket(),
                uploadResult.getObjectKey(),
                "EMAIL",
                "PARSED"
        );

        repository.save(candidate);
    }

}
