package com.instagram.domain.port.in.notification;

import java.util.UUID;

public interface MarkAllNotificationsReadUseCase {
    void markAllRead(Command command);

    record Command(UUID userId) {
    }
}
