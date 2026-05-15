package com.instagram.domain.exception;

import java.util.UUID;

public class UnauthorizedNotificationAccessException extends RuntimeException {
    public UnauthorizedNotificationAccessException(UUID notificationId, UUID userId) {
        super(String.format("User '%s' is not authorized to access notification '%s'", userId, notificationId));
    }
}
