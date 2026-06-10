package com.instagram.domain.port.out;

import java.time.Duration;
import java.util.List;

public interface MediaStoragePort {
    String uploadFile(String key, byte[] data, String contentType);

    String generatePresignedPutUrl(String key, Duration expiry);

    String getPublicUrl(String key);

    String initiateMultipartUpload(String key, String contentType);

    String generatePresignedPartUrl(String key, String uploadId, int partNumber, Duration expiry);

    String completeMultipartUpload(String key, String uploadId, List<PartETag> partETags);

    void abortMultipartUpload(String key, String uploadId);

    List<PartInfo> listUploadedParts(String key, String uploadId);

    record PartETag(int partNumber, String etag) {}

    record PartInfo(int partNumber, String etag, long sizeBytes) {}
}
