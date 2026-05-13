package com.instagram.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class Conversation {
    private UUID id;
    private String name;
    private boolean isGroup;
    private String pictureUrl;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isGroup() {
        return isGroup;
    }

    public String getPictureUrl() {
        return pictureUrl;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    private Conversation() {

    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Conversation conversation = new Conversation();

        public Builder id(UUID id) {
            conversation.id = id;
            return this;
        }

        public Builder name(String name) {
            conversation.name = name;
            return this;
        }

        public Builder isGroup(boolean isGroup) {
            conversation.isGroup = isGroup;
            return this;
        }

        public Builder pictureUrl(String pictureUrl) {
            conversation.pictureUrl = pictureUrl;
            return this;
        }

        public Builder createdAt(OffsetDateTime createdAt) {
            conversation.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(OffsetDateTime updatedAt) {
            conversation.updatedAt = updatedAt;
            return this;
        }

        public Builder createdById(UUID createdBy) {
            conversation.createdBy = createdBy;
            return this;
        }

        public Conversation build() {
            return conversation;
        }
    }

    private Builder copy() {
        return builder()
                .id(id)
                .name(name)
                .isGroup(isGroup)
                .pictureUrl(pictureUrl)
                .createdById(createdBy)
                .createdAt(createdAt)
                .updatedAt(updatedAt);
    }

    public Conversation withUpdatedName(String name) {
        return copy()
                .name(name)
                .updatedAt(OffsetDateTime.now())
                .build();
    }

}
