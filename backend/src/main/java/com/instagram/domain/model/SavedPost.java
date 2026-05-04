package com.instagram.domain.model;

import java.time.Instant;
import java.util.UUID;

public class SavedPost {
    private UUID postId;
    private UUID userId;

    private Instant savedAt;

    private SavedPost() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public UUID getPostId() {
        return postId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getSavedAt() {
        return savedAt;
    }

    public static final class Builder {
        private final SavedPost savedPost = new SavedPost();

        private Builder() {
        }

        public Builder postId(UUID postId) {
            savedPost.postId = postId;
            return this;
        }

        public Builder userId(UUID userId) {
            savedPost.userId = userId;
            return this;
        }

        public Builder savedAt(Instant savedAt) {
            savedPost.savedAt = savedAt;
            return this;
        }

        public SavedPost build() {

            if (savedPost.postId == null) {
                throw new IllegalArgumentException("postId cannot be null");
            }
            if (savedPost.userId == null) {
                throw new IllegalArgumentException("userId cannot be null");
            }
            return savedPost;
        }
    }

    public static SavedPost of(UUID postId, UUID userId) {
        return new Builder()
                .postId(postId)
                .userId(userId)
                .savedAt(Instant.now())
                .build();
    }
}
