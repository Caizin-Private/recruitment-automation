package com.example;

import com.example.infrastructure.storage.S3StorageService;
import com.example.infrastructure.storage.S3UploadResult;
import com.example.model.Candidate;
import com.example.repository.CandidateRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.io.File;

@SpringBootApplication
@EnableScheduling
public class RecruitmentAutomationApplication {

    public static void main(String[] args) {
        SpringApplication.run(RecruitmentAutomationApplication.class, args);
    }

    @Bean
    CommandLineRunner testManualUpload(S3StorageService s3Service, CandidateRepository repository) {
        return args -> {
            // 1. Setup Test File
            String testFolder = "manual-uploads";
            new File(testFolder).mkdirs();
            File testFile = new File(testFolder, "test_resume.pdf");

            // Create a dummy file if it doesn't exist
            if (!testFile.exists()) {
                System.out.println("⚠️ Creating dummy test file...");
                try (java.io.FileOutputStream fos = new java.io.FileOutputStream(testFile)) {
                    fos.write("Dummy Resume Content".getBytes());
                }
            }

            System.out.println("🧪 STARTING TEST UPLOAD...");

            // 2. Test Upload to S3
            try {
                S3UploadResult result = s3Service.upload(testFile, "MANUAL_TEST");
                System.out.println("☁️ Uploaded to S3: " + result.getObjectKey());

                // 3. Test Save to DB
                Candidate candidate = new Candidate();
                candidate.setEmail("test_" + System.currentTimeMillis() + "@example.com");
                candidate.setFullName("Test User");
                candidate.setSource(Candidate.SourceType.MANUAL);
                candidate.setResumeS3Key(result.getObjectKey());
                candidate.setResumeS3Bucket(result.getBucket());
                candidate.setStatus(Candidate.CandidateStatus.RECEIVED);

                repository.save(candidate);
                System.out.println("💾 Saved to DB with ID: " + candidate.getId());
                System.out.println("✅ TEST PASSED!");

            } catch (Exception e) {
                System.err.println("❌ TEST FAILED: " + e.getMessage());
                e.printStackTrace();
            }
        };
    }
}