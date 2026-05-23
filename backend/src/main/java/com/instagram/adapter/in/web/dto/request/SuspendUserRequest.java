package com.instagram.adapter.in.web.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Request payload for suspending a user account")
public record SuspendUserRequest(
        @Schema(description = "Human-readable reason for suspension; written to audit log",
                example = "Repeated community guideline violations")
        @NotBlank @Size(max = 500) String reason) {
}
