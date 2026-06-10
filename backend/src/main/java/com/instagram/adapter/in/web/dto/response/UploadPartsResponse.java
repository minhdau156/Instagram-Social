package com.instagram.adapter.in.web.dto.response;

import java.util.List;

public record UploadPartsResponse(
        String uploadId,
        List<UploadedPart> uploadedParts) {
    public record UploadedPart(int partNumber, String etag, long sizeBytes) {
    }
}
