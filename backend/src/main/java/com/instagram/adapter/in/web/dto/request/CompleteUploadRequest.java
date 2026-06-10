package com.instagram.adapter.in.web.dto.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

public record CompleteUploadRequest(
        @NotEmpty List<@Valid PartRequest> parts) {

    public record PartRequest(
            @Min(1) @Max(10000) int partNumber,
            @NotBlank String etag) {
    }

}
