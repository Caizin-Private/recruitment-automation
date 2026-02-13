package com.example.service;

import com.example.dto.ParsedResume;
import com.example.infrastructure.storage.S3StorageService;
import com.example.infrastructure.storage.S3UploadResult;
import com.example.model.Candidate;
import com.example.repository.CandidateRepository;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class ResumeProcessingService {

    private final ResumeTextExtractor extractor;
    private final ResumeParserService parser;
    private final S3StorageService s3Service;
    private final CandidateRepository repository;

    public ResumeProcessingService(
            ResumeTextExtractor extractor,
            ResumeParserService parser,
            S3StorageService s3Service,
            CandidateRepository repository
    ) {
        this.extractor = extractor;
        this.parser = parser;
        this.s3Service = s3Service;
        this.repository = repository;
    }

    public void process(File file) {

        // 1️⃣ Extract text
        String text = extractor.extractText(file);

        // 2️⃣ Parse name & email
        ParsedResume parsed = parser.parse(text);

        // 3️⃣ Upload original resume to S3
        S3UploadResult uploadResult =
                s3Service.upload(file, "EMAIL");

        // 4️⃣ Persist metadata
        Candidate candidate = new Candidate();
        candidate.setFullName(parsed.fullName());
        candidate.setEmail(parsed.email());
        candidate.setResumeS3Bucket(uploadResult.getBucket());
        candidate.setResumeS3Key(uploadResult.getObjectKey());
        candidate.setSource(Candidate.SourceType.EMAIL);
        candidate.setStatus(Candidate.CandidateStatus.PARSED);

        repository.save(candidate);

        // 5️⃣ Cleanup
        file.delete();
    }
}
