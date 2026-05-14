package com.instagram.domain.exception;

import java.util.UUID;

public class NotificationNotFoundException extends RuntimeException {
    public NotificationNotFoundException(UUID notificationId) {
        super("Notification not found: " + notificationId);
    }

    public static NotificationNotFoundException withId(UUID id) {
        return new NotificationNotFoundException(id);
    }
}
