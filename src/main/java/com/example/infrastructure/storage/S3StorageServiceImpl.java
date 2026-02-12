package com.example.infrastructure.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.InputStream;
import java.util.UUID;

@Service
public class S3StorageServiceImpl implements S3StorageService {

    private final S3Client s3Client;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.base-folder}")
    private String baseFolder;

    public S3StorageServiceImpl(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public S3UploadResult upload(File file, String source) {
        String objectKey = generateObjectKey(file.getName(), source);

        // 1. Detect the REAL content type using your helper method
        String contentType = detectContentType(file.getName());

        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(contentType) 
                    .build();

            s3Client.putObject(request, RequestBody.fromFile(file));

            // 2. Return the correct content type in the result
            return new S3UploadResult(bucket, objectKey, file.length(), contentType);

        } catch (Exception e) {
            throw new RuntimeException("Failed to upload to S3: " + e.getMessage(), e);
        }
    }

    @Override
    public InputStream download(String objectKey) {

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        return s3Client.getObject(request);
    }

    @Override
    public void delete(String objectKey) {

        DeleteObjectRequest request = DeleteObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build();

        s3Client.deleteObject(request);
    }

    // ---------- helpers ----------

    private String generateObjectKey(String filename, String source) {
        return String.format("%s/%s/%s-%s", baseFolder, source.toLowerCase(), UUID.randomUUID(), filename);
    }

    private String detectContentType(String filename) {
        if (filename.endsWith(".pdf")) return "application/pdf";
        if (filename.endsWith(".doc")) return "application/msword";
        if (filename.endsWith(".docx"))
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "application/octet-stream";
    }
}
