package com.example.infrastructure.storage;

public class S3UploadResult {

    private final String bucket;
    private final String objectKey;
    private final long fileSize;
    private final String contentType;

    public S3UploadResult(
            String bucket,
            String objectKey,
            long fileSize,
            String contentType) {
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.fileSize = fileSize;
        this.contentType = contentType;
    }

    public String getBucket() { return bucket; }
    public String getObjectKey() { return objectKey; }
    public long getFileSize() { return fileSize; }
    public String getContentType() { return contentType; }
}
