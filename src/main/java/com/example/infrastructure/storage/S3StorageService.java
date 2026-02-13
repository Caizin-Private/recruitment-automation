package com.example.infrastructure.storage;

import java.io.File;
import java.io.InputStream;

public interface S3StorageService {

    S3UploadResult upload(File file, String source);

    InputStream download(String objectKey);

    void delete(String objectKey);
}
