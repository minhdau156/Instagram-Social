package com.instagram.adapter.out.storage;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.instagram.domain.exception.MediaUploadException;
import com.instagram.domain.port.out.MediaStoragePort;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;

@Component
public class MinioStorageAdapter implements MediaStoragePort {

    private final MinioClient minioClient;
    private final String bucket;
    private final String endpoint;
    private final String cdnBaseUrl;

    public MinioStorageAdapter(MinioClient minioClient,
            @Value("${app.minio.bucket}") String bucket,
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.cdn-base-url}") String cdnBaseUrl) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.endpoint = endpoint;
        this.cdnBaseUrl = cdnBaseUrl;
    }

    @Override
    public String uploadFile(String key, byte[] data, String contentType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(data), data.length, -1)
                    .contentType(contentType)
                    .build());
            return String.format("%s/%s/%s", cdnBaseUrl, bucket, key);
        } catch (Exception e) {
            throw new MediaUploadException("Failed to upload file to MinIO");
        }
    }

    @Override
    public String generatePresignedPutUrl(String key, Duration expiry) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(key)
                            .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new MediaUploadException("Failed to generate presigned put URL for MinIO");
        }
    }

    @Override
    public String getPublicUrl(String key) {
        return String.format("%s/%s/%s", cdnBaseUrl, bucket, key);
    }

}
