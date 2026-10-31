package com.instagram.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioAsyncClient;
import io.minio.MinioClient;

@Configuration
public class MinioConfig {

    // Used only to generate presigned URLs the browser calls directly, so it must be
    // built from a browser-reachable endpoint — never app.minio.endpoint, which in
    // local dev is the Docker-internal "minio" hostname the browser can't resolve.
    // region() is required here: without it, the SDK calls GetBucketLocation against
    // this endpoint on first use to auto-detect the region, and that call runs from
    // the backend process — which can't actually reach this browser-facing endpoint.
    @Bean
    public MinioClient presignMinioClient(
            @Value("${app.minio.cdn-base-url}") String cdnBaseUrl,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(cdnBaseUrl)
                .region("us-east-1")
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public MultipartMinioClient multipartMinioClient(
            @Value("${app.minio.endpoint}") String endpoint,
            @Value("${app.minio.access-key}") String accessKey,
            @Value("${app.minio.secret-key}") String secretKey) {
        MinioAsyncClient asyncClient = MinioAsyncClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
        return new MultipartMinioClient(asyncClient);
    }
}
