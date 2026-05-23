package com.instagram.adapter.in.web.dto.request;

import java.util.UUID;

import com.instagram.domain.model.ReportEntityType;
import com.instagram.domain.model.ReportReason;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for reporting content")
public record ReportRequest(
        @Schema(description = "Type of entity being reported", example = "POST")
        @NotNull ReportEntityType entityType,

        @Schema(description = "UUID of the reported entity", example = "123e4567-e89b-12d3-a456-426614174000")
        @NotNull UUID entityId,

        @Schema(description = "Reason for the report",
                allowableValues = {"SPAM", "HATE_SPEECH", "NUDITY", "VIOLENCE", "HARASSMENT",
                        "FALSE_INFORMATION", "SELF_HARM", "OTHER"})
        @NotNull ReportReason reason,

        @Schema(description = "Optional additional details", example = "This post is promoting fake products")
        @Size(max = 1000) String details) {
}
