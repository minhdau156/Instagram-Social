package com.instagram.adapter.in.web.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddCommentRequest(
        @NotBlank(message = "Comment content cannot be blank") @Size(max = 2200, message = "Comment content cannot exceed 2200 characters") String content,

        UUID parentId // nullable — null means top-level comment
) {
}
