package com.instagram.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProcessMediaRequest(
        @NotBlank String mediaUrl,
        @NotBlank String postId
) {}
