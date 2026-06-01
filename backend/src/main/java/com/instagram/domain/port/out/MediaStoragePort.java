package com.instagram.domain.port.out;

import java.time.Duration;

public interface MediaStoragePort {
    String uploadFile(String key, byte[] data, String contentType);

    String generatePresignedPutUrl(String key, Duration expiry);

    String getPublicUrl(String key);
}
