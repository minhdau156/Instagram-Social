package com.instagram.adapter.in.web.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EditCommentRequest(
        @NotBlank(message = "Comment content cannot be blank") @Size(max = 2200, message = "Comment content cannot exceed 2200 characters") String content) {
}
