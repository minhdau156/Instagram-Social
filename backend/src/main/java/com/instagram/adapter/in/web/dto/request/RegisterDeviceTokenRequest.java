package com.instagram.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record RegisterDeviceTokenRequest(
        @NotBlank String token,
        @NotBlank String platform) {
}
