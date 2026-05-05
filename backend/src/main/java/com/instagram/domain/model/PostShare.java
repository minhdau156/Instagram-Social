package com.instagram.domain.model;

import java.time.Instant;
import java.util.UUID;

public class PostShare {
    private UUID id;
    private UUID postId;
    private UUID sharerId;
    private UUID recipientId; // nullable — only for ShareType.DM
    private ShareType shareType;
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public UUID getPostId() {
        return postId;
    }

    public UUID getSharerId() {
        return sharerId;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public ShareType getShareType() {
        return shareType;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public PostShare() {

    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final PostShare postShare = new PostShare();

        private Builder() {

        }

        public Builder id(UUID id) {
            postShare.id = id;
            return this;
        }

        public Builder postId(UUID postId) {
            postShare.postId = postId;
            return this;
        }

        public Builder sharerId(UUID sharerId) {
            postShare.sharerId = sharerId;
            return this;
        }

        public Builder recipientId(UUID recipientId) {
            postShare.recipientId = recipientId;
            return this;
        }

        public Builder shareType(ShareType shareType) {
            postShare.shareType = shareType;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            postShare.createdAt = createdAt;
            return this;
        }

        public PostShare build() {
            if (postShare.id == null) {
                throw new IllegalArgumentException("id cannot be null");
            }
            if (postShare.postId == null) {
                throw new IllegalArgumentException("postId cannot be null");
            }
            if (postShare.sharerId == null) {
                throw new IllegalArgumentException("sharerId cannot be null");
            }
            if (postShare.shareType == null) {
                throw new IllegalArgumentException("shareType cannot be null");
            }
            return postShare;
        }
    }

    public static PostShare of(UUID postId, UUID sharerId, UUID recipientId, ShareType shareType) {
        return new Builder()
                .id(UUID.randomUUID())
                .postId(postId)
                .sharerId(sharerId)
                .recipientId(recipientId)
                .shareType(shareType)
                .createdAt(Instant.now())
                .build();
    }
}
