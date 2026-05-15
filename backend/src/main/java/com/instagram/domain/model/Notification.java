package com.instagram.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class Notification {
    public enum NotificationType {
        LIKE_POST,
        LIKE_COMMENT,
        COMMENT_POST,
        REPLY_COMMENT,
        FOLLOW,
        FOLLOW_REQUEST,
        FOLLOW_ACCEPTED,
        MENTION_POST,
        MENTION_COMMENT,
        DIRECT_MESSAGE,
        GROUP_MESSAGE,
        POST_SHARED
    }

    public enum EntityType {
        POST,
        COMMENT,
        FOLLOW,
        MESSAGE
    }

    private UUID id;
    private UUID recipientId;
    private UUID actorId;
    private EntityType entityType;
    private UUID entityId;
    private NotificationType type;
    private boolean isRead;
    private OffsetDateTime createdAt;

    private Notification() {

    }

    public UUID getId() {
        return id;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

    public NotificationType getType() {
        return type;
    }

    public boolean isRead() {
        return isRead;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public Notification withRead() {
        return copy().isRead(true).build();
    }

    private Builder copy() {
        return builder()
                .id(id)
                .recipientId(recipientId)
                .actorId(actorId)
                .entityType(entityType)
                .entityId(entityId)
                .type(type)
                .isRead(isRead)
                .createdAt(createdAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Notification notification = new Notification();

        public Builder() {

        }

        public Builder id(UUID id) {
            notification.id = id;
            return this;
        }

        public Builder recipientId(UUID recipientId) {
            notification.recipientId = recipientId;
            return this;
        }

        public Builder actorId(UUID actorId) {
            notification.actorId = actorId;
            return this;
        }

        public Builder entityType(EntityType entityType) {
            notification.entityType = entityType;
            return this;
        }

        public Builder entityId(UUID entityId) {
            notification.entityId = entityId;
            return this;
        }

        public Builder type(NotificationType type) {
            notification.type = type;
            return this;
        }

        public Builder isRead(boolean isRead) {
            notification.isRead = isRead;
            return this;
        }

        public Builder createdAt(OffsetDateTime createdAt) {
            notification.createdAt = createdAt;
            return this;
        }

        public Notification build() {
            return notification;
        }
    }
}
