package com.instagram.domain.model;

import java.util.UUID;

public record NotificationSettings(
        UUID userId,
        boolean likesEnabled,
        boolean commentsEnabled,
        boolean followsEnabled,
        boolean messagesEnabled,
        boolean pushEnabled
) {
    public static NotificationSettings defaultsFor(UUID userId) {
        return new NotificationSettings(userId, true, true, true, true, true);
    }
}
