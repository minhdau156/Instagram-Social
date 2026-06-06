package com.instagram.application.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.domain.exception.NotificationNotFoundException;
import com.instagram.domain.exception.UnauthorizedNotificationAccessException;
import com.instagram.domain.model.Notification;
import com.instagram.domain.model.Notification.NotificationType;
import com.instagram.domain.model.NotificationSettings;
import com.instagram.domain.port.in.notification.CreateNotificationUseCase;
import com.instagram.domain.port.in.notification.GetNotificationSettingsUseCase;
import com.instagram.domain.port.in.notification.GetNotificationsUseCase;
import com.instagram.domain.port.in.notification.MarkAllNotificationsReadUseCase;
import com.instagram.domain.port.in.notification.MarkNotificationReadUseCase;
import com.instagram.domain.port.in.notification.UpdateNotificationSettingsUseCase;
import com.instagram.domain.port.out.NotificationRepository;
import com.instagram.domain.port.out.NotificationSettingsRepository;
import com.instagram.infrastructure.util.CursorEncoder;

@Service
public class NotificationService
        implements CreateNotificationUseCase, GetNotificationsUseCase, MarkAllNotificationsReadUseCase,
        MarkNotificationReadUseCase, GetNotificationSettingsUseCase, UpdateNotificationSettingsUseCase {

    private final NotificationRepository notificationRepository;
    private final NotificationSettingsRepository notificationSettingsRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public NotificationService(NotificationRepository notificationRepository,
            NotificationSettingsRepository notificationSettingsRepository,
            SimpMessagingTemplate simpMessagingTemplate) {
        this.notificationRepository = notificationRepository;
        this.notificationSettingsRepository = notificationSettingsRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @Override
    @Transactional
    @CacheEvict(value = "notifSettings", key = "'notifSettings:' + #command.userId")
    public NotificationSettings updateSettings(UpdateNotificationSettingsUseCase.Command command) {
        NotificationSettings settings = notificationSettingsRepository.findByUserId(command.userId())
                .orElse(NotificationSettings.defaultsFor(command.userId()));
        NotificationSettings updated = settings
                .withLikesEnabled(command.likesEnabled())
                .withCommentsEnabled(command.commentsEnabled())
                .withFollowsEnabled(command.followsEnabled())
                .withMessagesEnabled(command.messagesEnabled())
                .withPushEnabled(command.pushEnabled());
        return notificationSettingsRepository.save(updated);
    }

    @Override
    @Cacheable(value = "notifSettings", key = "'notifSettings:' + #query.userId")
    public NotificationSettings getSettings(GetNotificationSettingsUseCase.Query query) {
        return notificationSettingsRepository.findByUserId(query.userId())
                .orElse(NotificationSettings.defaultsFor(query.userId()));
    }

    @Override
    @Transactional
    public void markRead(MarkNotificationReadUseCase.Command command) {
        Notification notification = notificationRepository.findById(command.notificationId())
                .orElseThrow(() -> NotificationNotFoundException.withId(command.notificationId()));
        if (!notification.getRecipientId().equals(command.userId())) {
            throw new UnauthorizedNotificationAccessException(command.notificationId(), command.userId());
        }
        notificationRepository.markAsRead(command.notificationId());
    }

    @Override
    public void markAllRead(MarkAllNotificationsReadUseCase.Command command) {
        notificationRepository.markAllAsRead(command.userId());
    }

    @Override
    public List<Notification> getNotifications(GetNotificationsUseCase.Query query) {
        CursorEncoder.DecodedCursor decoded = query.cursor() != null
                ? CursorEncoder.decode(query.cursor())
                : null;
        String cursorTs = decoded != null ? decoded.createdAt().toString() : null;
        UUID cursorId = decoded != null ? decoded.id() : null;
        return notificationRepository.findByRecipientId(query.userId(), cursorTs, cursorId, query.size());
    }

    @Override
    @Transactional
    public Notification createNotification(CreateNotificationUseCase.Command command) {
        NotificationSettings settings = notificationSettingsRepository.findByUserId(command.recipientId())
                .orElse(NotificationSettings.defaultsFor(command.recipientId()));
        if (isSuppressed(command.type(), settings)) {
            return null;
        }

        Notification notification = Notification.builder()
                .recipientId(command.recipientId())
                .actorId(command.actorId())
                .entityType(command.entityType())
                .entityId(command.entityId())
                .type(command.type())
                .isRead(false)
                .createdAt(OffsetDateTime.now())
                .build();
        Notification saved = notificationRepository.save(notification);

        simpMessagingTemplate.convertAndSendToUser(
                saved.getRecipientId().toString(),
                "/topic/notifications",
                saved);
        return saved;
    }

    private boolean isSuppressed(NotificationType type, NotificationSettings settings) {
        return switch (type) {
            case LIKE_POST, LIKE_COMMENT -> !settings.likesEnabled();
            case COMMENT_POST, REPLY_COMMENT, MENTION_POST, MENTION_COMMENT -> !settings.commentsEnabled();
            case FOLLOW, FOLLOW_REQUEST, FOLLOW_ACCEPTED -> !settings.followsEnabled();
            case DIRECT_MESSAGE, GROUP_MESSAGE -> !settings.messagesEnabled();
            case POST_SHARED -> false;
        };
    }
}
