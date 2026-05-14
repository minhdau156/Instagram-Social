package com.instagram.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import com.instagram.domain.model.NotificationSettings;

public interface NotificationSettingsRepository {
    Optional<NotificationSettings> findByUserId(UUID userId);

    NotificationSettings save(NotificationSettings settings);
}
