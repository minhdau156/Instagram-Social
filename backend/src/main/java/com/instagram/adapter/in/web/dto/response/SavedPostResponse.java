package com.instagram.adapter.in.web.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.instagram.domain.model.SavedPost;

public record SavedPostResponse(UUID postId, UUID userId, Instant savedAt) {
    public static SavedPostResponse from(SavedPost savedPost) {
        return new SavedPostResponse(savedPost.getPostId(), savedPost.getUserId(), savedPost.getSavedAt());
    }
}
