package com.instagram.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class UserBlock {
    private UUID blockerId;
    private UUID blockedId;
    private OffsetDateTime createdAt;

    private UserBlock() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final UserBlock userBlock = new UserBlock();

        public Builder blockerId(UUID blockerId) {
            userBlock.blockerId = blockerId;
            return this;
        }

        public Builder blockedId(UUID blockedId) {
            userBlock.blockedId = blockedId;
            return this;
        }

        public Builder createdAt(OffsetDateTime createdAt) {
            userBlock.createdAt = createdAt;
            return this;
        }

        public UserBlock build() {
            if (userBlock.blockerId == null || userBlock.blockedId == null || userBlock.createdAt == null) {
                throw new IllegalStateException("Missing required fields");
            }
            if (userBlock.blockerId.equals(userBlock.blockedId)) {
                throw new IllegalArgumentException("Blocker and blocked IDs must be different");
            }
            return userBlock;
        }
    }

    public UUID getBlockerId() {
        return blockerId;
    }

    public UUID getBlockedId() {
        return blockedId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
