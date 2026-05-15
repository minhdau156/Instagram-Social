package com.instagram.domain.port.in.notification;

import java.util.UUID;

public interface MarkNotificationReadUseCase {

    void markRead(Command command);

    record Command(UUID notificationId, UUID userId) {
    }
}
