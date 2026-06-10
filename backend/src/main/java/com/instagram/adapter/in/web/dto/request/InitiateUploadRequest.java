package com.instagram.adapter.in.web.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record InitiateUploadRequest(
        @NotBlank String filename,
        @NotBlank String contentType, // e.g. "video/mp4"
        @Min(1) @Max(10000) int totalParts,
        @Positive long totalSizeBytes, // capped at 2 GB in controller
        @Positive long partSizeBytes) {

}
