package com.instagram.adapter.out.storage;

import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.instagram.domain.exception.MediaUploadException;
import com.instagram.domain.port.out.MediaStoragePort;
import com.instagram.infrastructure.config.MultipartMinioClient;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.PutObjectArgs;
import io.minio.http.Method;
import io.minio.messages.Part;
import lombok.extern.slf4j.Slf4j;

import java.util.stream.Collectors;

@Slf4j
@Component
public class MinioStorageAdapter implements MediaStoragePort {

    private final MultipartMinioClient minioClient;
    private final String bucket;
    private final String endpoint;
    private final String cdnBaseUrl;

    public MinioStorageAdapter(MultipartMinioClient minioClient,
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
                    .build()).get();
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

    @Override
    public String initiateMultipartUpload(String key, String contentType) {
        try {
            return minioClient.createMultipartUpload(bucket, key, contentType);
        } catch (Exception e) {
            throw new MediaUploadException("Failed to initiate multipart upload: " + e.getMessage());
        }
    }

    @Override
    public String generatePresignedPartUrl(String key, String uploadId, int partNumber, Duration expiry) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(key)
                            .expiry((int) expiry.toSeconds(), TimeUnit.SECONDS)
                            .extraQueryParams(Map.of(
                                    "uploadId", uploadId,
                                    "partNumber", String.valueOf(partNumber)))
                            .build());
        } catch (Exception e) {
            throw new MediaUploadException("Failed to generate presigned part URL: " + e.getMessage());
        }
    }

    @Override
    public String completeMultipartUpload(String key, String uploadId, List<PartETag> partETags) {
        try {
            Part[] parts = partETags.stream()
                    .map(p -> new Part(p.partNumber(), p.etag()))
                    .toArray(Part[]::new);
            minioClient.completeMultipartUpload(bucket, key, uploadId, parts);
            return String.format("%s/%s/%s", cdnBaseUrl, bucket, key);
        } catch (Exception e) {
            throw new MediaUploadException("Failed to complete multipart upload: " + e.getMessage());
        }
    }

    @Override
    public void abortMultipartUpload(String key, String uploadId) {
        try {
            minioClient.abortMultipartUpload(bucket, key, uploadId);
        } catch (Exception e) {
            log.warn("Failed to abort multipart upload key={} uploadId={}: {}", key, uploadId, e.getMessage());
        }
    }

    @Override
    public List<PartInfo> listUploadedParts(String key, String uploadId) {
        try {
            return minioClient.listParts(bucket, key, uploadId).stream()
                    .map(p -> new PartInfo(p.partNumber(), p.etag(), p.partSize()))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new MediaUploadException("Failed to list uploaded parts: " + e.getMessage());
        }
    }

}
