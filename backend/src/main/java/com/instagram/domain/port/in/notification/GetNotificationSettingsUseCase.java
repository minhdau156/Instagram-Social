package com.instagram.domain.port.in.notification;

import java.util.UUID;

import com.instagram.domain.model.NotificationSettings;

public interface GetNotificationSettingsUseCase {
    NotificationSettings getSettings(Query query);

    record Query(UUID userId) {
    }
}
