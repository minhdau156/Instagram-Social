package com.instagram.adapter.in.web.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.instagram.domain.model.Notification;
import com.instagram.domain.model.User;

public record NotificationResponse(
        UUID id,
        Notification.NotificationType type,
        Notification.EntityType entityType,
        UUID entityId,
        String actorUsername,
        String actorAvatarUrl,
        boolean isRead,
        OffsetDateTime createdAt) {

    public static NotificationResponse from(Notification notification, User actor) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getEntityType(),
                notification.getEntityId(),
                actor != null ? actor.getUsername() : null,
                actor != null ? actor.getProfilePictureUrl() : null,
                notification.isRead(),
                notification.getCreatedAt());
    }
}
