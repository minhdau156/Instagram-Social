package com.instagram.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class ConversationMember {

    public enum Role { OWNER, MEMBER }

    private UUID conversationId;
    private UUID userId;
    private Role role;
    private OffsetDateTime joinedAt;
    private OffsetDateTime leftAt;

    private ConversationMember() {

    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final ConversationMember conversationMember = new ConversationMember();

        public Builder conversationId(UUID conversationId) {
            conversationMember.conversationId = conversationId;
            return this;
        }

        public Builder userId(UUID userId) {
            conversationMember.userId = userId;
            return this;
        }

        public Builder role(Role role) {
            conversationMember.role = role;
            return this;
        }

        public Builder joinedAt(OffsetDateTime joinedAt) {
            conversationMember.joinedAt = joinedAt;
            return this;
        }

        public Builder leftAt(OffsetDateTime leftAt) {
            conversationMember.leftAt = leftAt;
            return this;
        }

        public ConversationMember build() {
            return conversationMember;
        }
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Role getRole() {
        return role;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public OffsetDateTime getLeftAt() {
        return leftAt;
    }
}
