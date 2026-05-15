package com.instagram.domain.port.in.notification;

import java.util.UUID;

import com.instagram.domain.model.Notification;

public interface CreateNotificationUseCase {
    Notification createNotification(Command command);

    record Command(Notification.NotificationType type, UUID recipientId, UUID actorId,
            Notification.EntityType entityType, UUID entityId) {
    }
}
