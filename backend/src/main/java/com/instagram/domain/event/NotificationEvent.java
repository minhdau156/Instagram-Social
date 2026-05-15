package com.instagram.domain.event;

import java.util.UUID;

import org.springframework.context.ApplicationEvent;

import com.instagram.domain.model.Notification;

public class NotificationEvent extends ApplicationEvent {

    private Notification.NotificationType type;
    private UUID recipientId;
    private UUID actorId;
    private Notification.EntityType entityType;
    private UUID entityId;

    public NotificationEvent(Object source, Notification.NotificationType type, UUID recipientId, UUID actorId,
            Notification.EntityType entityType,
            UUID entityId) {
        super(source);
        this.type = type;
        this.recipientId = recipientId;
        this.actorId = actorId;
        this.entityType = entityType;
        this.entityId = entityId;
    }

    public Notification.NotificationType getType() {
        return type;
    }

    public UUID getRecipientId() {
        return recipientId;
    }

    public UUID getActorId() {
        return actorId;
    }

    public Notification.EntityType getEntityType() {
        return entityType;
    }

    public UUID getEntityId() {
        return entityId;
    }

}
