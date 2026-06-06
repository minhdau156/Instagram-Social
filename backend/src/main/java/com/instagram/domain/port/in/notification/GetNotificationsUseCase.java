package com.instagram.domain.port.in.notification;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.Notification;

public interface GetNotificationsUseCase {
    List<Notification> getNotifications(Query query);

    record Query(UUID userId, String cursor, int size) {
    }
}
