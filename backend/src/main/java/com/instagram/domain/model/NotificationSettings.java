package com.instagram.domain.model;

import java.util.UUID;

public record NotificationSettings(
        UUID userId,
        boolean likesEnabled,
        boolean commentsEnabled,
        boolean followsEnabled,
        boolean messagesEnabled,
        boolean pushEnabled) {
    public static NotificationSettings defaultsFor(UUID userId) {
        return new NotificationSettings(userId, true, true, true, true, true);
    }

    public NotificationSettings builder() {
        return this;
    }

    public NotificationSettings withUserId(UUID userId) {
        return new NotificationSettings(userId, likesEnabled, commentsEnabled, followsEnabled, messagesEnabled,
                pushEnabled);
    }

    public NotificationSettings withLikesEnabled(boolean likesEnabled) {
        return new NotificationSettings(userId, likesEnabled, commentsEnabled, followsEnabled, messagesEnabled,
                pushEnabled);
    }

    public NotificationSettings withCommentsEnabled(boolean commentsEnabled) {
        return new NotificationSettings(userId, likesEnabled, commentsEnabled, followsEnabled, messagesEnabled,
                pushEnabled);
    }

    public NotificationSettings withFollowsEnabled(boolean followsEnabled) {
        return new NotificationSettings(userId, likesEnabled, commentsEnabled, followsEnabled, messagesEnabled,
                pushEnabled);
    }

    public NotificationSettings withMessagesEnabled(boolean messagesEnabled) {
        return new NotificationSettings(userId, likesEnabled, commentsEnabled, followsEnabled, messagesEnabled,
                pushEnabled);
    }

    public NotificationSettings withPushEnabled(boolean pushEnabled) {
        return new NotificationSettings(userId, likesEnabled, commentsEnabled, followsEnabled, messagesEnabled,
                pushEnabled);
    }
}
