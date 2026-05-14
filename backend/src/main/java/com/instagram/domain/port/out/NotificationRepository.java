package com.instagram.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.instagram.domain.model.Notification;

public interface NotificationRepository {
    Notification save(Notification notification);

    List<Notification> findByRecipientId(UUID recipientId, Pageable pageable);

    Optional<Notification> findById(UUID notificationId);

    void markAsRead(UUID notificationId);

    void markAllAsRead(UUID recipientId);

    long getUnreadCount(UUID recipientId);
}
