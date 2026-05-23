package com.instagram.adapter.in.web.dto.request;

import com.instagram.domain.port.in.admin.ReviewReportUseCase;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request payload for reviewing a moderation report")
public record ReviewReportRequest(
                @Schema(description = "Review decision", allowableValues = {
                                "RESOLVE", "DISMISS",
                                "MARK_REVIEWED" }) @NotNull ReviewReportUseCase.ReviewAction action) {
}
