package com.instagram.domain.port.in.notification;

import java.util.UUID;

import com.instagram.domain.model.NotificationSettings;

public interface UpdateNotificationSettingsUseCase {
    NotificationSettings updateSettings(Command command);

    record Command(UUID userId, boolean likesEnabled, boolean commentsEnabled, boolean followsEnabled,
            boolean messagesEnabled, boolean pushEnabled) {
    }
}
