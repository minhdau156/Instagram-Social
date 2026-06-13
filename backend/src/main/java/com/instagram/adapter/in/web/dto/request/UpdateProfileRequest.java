package com.instagram.adapter.in.web.dto.request;

import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Request payload for updating user profile")
public record UpdateProfileRequest(
                @Schema(description = "User's full name", example = "John Doe Updated") @Size(max = 150) String fullName,

                @Schema(description = "User's biography", example = "New updated bio text") @Size(max = 500) String bio,

                @Schema(description = "Optional flag to toggle private account status", example = "true") Boolean isPrivate) {
}
