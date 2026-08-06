package com.instagram.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.repository.NotificationJpaRepository;
import com.instagram.adapter.out.persistence.repository.NotificationSettingsJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserJpaRepository;
import com.instagram.domain.model.Notification;
import com.instagram.domain.model.NotificationSettings;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.UserStatus;

public class NotificationPersistenceAdapterIT extends PostgresIntegrationTest {
    @Autowired
    NotificationJpaRepository notificationJpaRepository;
    @Autowired
    NotificationSettingsJpaRepository notificationSettingsJpaRepository;
    @Autowired
    UserJpaRepository userJpaRepository;

    NotificationPersistenceAdapter notificationPersistenceAdapter;
    NotificationSettingsPersistenceAdapter notificationSettingsPersistenceAdapter;
    UUID userId;
    UUID actorId;

    // notifications.recipient_id / actor_id and notification_settings.user_id
    // are real FKs to users.id — every test needs actually-persisted parent
    // rows rather than bare UUID.randomUUID().
    private UUID persistUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userJpaRepository.save(UserJpaEntity.builder()
                .username("notif_" + suffix)
                .email("notif_" + suffix + "@example.com")
                .fullName("Notification Test User")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build()).getId();
    }

    @BeforeEach
    void setUp() {
        notificationPersistenceAdapter = new NotificationPersistenceAdapter(
                notificationJpaRepository);
        notificationSettingsPersistenceAdapter = new NotificationSettingsPersistenceAdapter(
                notificationSettingsJpaRepository);

        userId = persistUser();
        actorId = persistUser();

        notification = Notification.builder()
                .type(Notification.NotificationType.LIKE_POST)
                .entityId(UUID.randomUUID())
                .entityType(Notification.EntityType.POST)
                .actorId(actorId)
                .recipientId(userId)
                .isRead(false)
                .createdAt(OffsetDateTime.now())
                .build();

        notificationSettings = NotificationSettings.defaultsFor(userId);

    }

    Notification notification;
    NotificationSettings notificationSettings;

    @Test
    void createNotification_persistsNotificationRecord_shouldSuccess() {
        Notification saved = notificationPersistenceAdapter.save(notification);
        assertNotNull(saved.getId());
        assertEquals(Notification.NotificationType.LIKE_POST, saved.getType());
        assertEquals(Notification.EntityType.POST, saved.getEntityType());
        assertEquals(notification.getActorId(), saved.getActorId());
        assertEquals(notification.getRecipientId(), saved.getRecipientId());
        assertEquals(notification.isRead(), saved.isRead());

    }

    @Test
    void findByRecipientId_shouldReturnNotifications_shouldSuccess() {
        notification = notificationPersistenceAdapter.save(notification);
        List<Notification> notifications = notificationPersistenceAdapter
                .findByRecipientId(notification.getRecipientId(), null, null, 20);
        assertEquals(1, notifications.size());
        assertEquals(notification.getActorId(), notifications.get(0).getActorId());
        assertEquals(notification.getRecipientId(), notifications.get(0).getRecipientId());
        assertEquals(notification.getEntityId(), notifications.get(0).getEntityId());
        assertEquals(notification.getEntityType(), notifications.get(0).getEntityType());
        assertEquals(notification.getType(), notifications.get(0).getType());
        assertEquals(notification.isRead(), notifications.get(0).isRead());
        assertEquals(notification.getCreatedAt(), notifications.get(0).getCreatedAt());
    }

    @Test
    void findById_shouldReturnNotification_shouldSuccess() {
        notification = notificationPersistenceAdapter.save(notification);
        Notification notification2 = notificationPersistenceAdapter.findById(notification.getId()).get();
        assertEquals(notification.getActorId(), notification2.getActorId());
        assertEquals(notification.getRecipientId(), notification2.getRecipientId());
        assertEquals(notification.getEntityId(), notification2.getEntityId());
        assertEquals(notification.getEntityType(), notification2.getEntityType());
        assertEquals(notification.getType(), notification2.getType());
        assertEquals(notification.isRead(), notification2.isRead());
        assertEquals(notification.getCreatedAt(), notification2.getCreatedAt());
    }

    @Test
    void markAsRead_shouldMarkAsRead_shouldSuccess() {
        notification = notificationPersistenceAdapter.save(notification);
        notificationPersistenceAdapter.markAsRead(notification.getId());
        Notification notification2 = notificationPersistenceAdapter.findById(notification.getId()).get();
        assertEquals(true, notification2.isRead());
    }

    @Test
    void markAllAsRead_shouldMarkAllAsRead_shouldSuccess() {
        notification = notificationPersistenceAdapter.save(notification);
        Notification notification2 = Notification.builder()
                .type(Notification.NotificationType.LIKE_POST)
                .entityId(UUID.randomUUID())
                .entityType(Notification.EntityType.POST)
                .actorId(actorId)
                .recipientId(userId)
                .isRead(false)
                .createdAt(OffsetDateTime.now())
                .build();
        Notification notification3 = Notification.builder()
                .type(Notification.NotificationType.LIKE_POST)
                .entityId(UUID.randomUUID())
                .entityType(Notification.EntityType.POST)
                .actorId(actorId)
                .recipientId(userId)
                .isRead(false)
                .createdAt(OffsetDateTime.now())
                .build();
        notification2 = notificationPersistenceAdapter.save(notification2);
        notification3 = notificationPersistenceAdapter.save(notification3);
        notificationPersistenceAdapter.markAllAsRead(userId);
        Notification savedNotification = notificationPersistenceAdapter.findById(notification.getId()).get();
        Notification savedNotification2 = notificationPersistenceAdapter.findById(notification2.getId()).get();
        Notification savedNotification3 = notificationPersistenceAdapter.findById(notification3.getId()).get();
        assertEquals(true, savedNotification.isRead());
        assertEquals(true, savedNotification2.isRead());
        assertEquals(true, savedNotification3.isRead());
    }

    @Test
    void getUnreadCount_shouldReturnUnreadCount_shouldSuccess() {
        notification = notificationPersistenceAdapter.save(notification);
        long unreadCount = notificationPersistenceAdapter.getUnreadCount(notification.getRecipientId());
        assertEquals(1, unreadCount);
    }
    // NotificationSettings

    @Test
    void findByUserId_shouldReturnNotificationSettings_shouldSuccess() {
        notificationSettings = notificationSettingsPersistenceAdapter.save(notificationSettings);
        NotificationSettings notificationSettings2 = notificationSettingsPersistenceAdapter
                .findByUserId(notificationSettings.userId()).get();
        assertEquals(notificationSettings.userId(), notificationSettings2.userId());
        assertEquals(notificationSettings.likesEnabled(), notificationSettings2.likesEnabled());
        assertEquals(notificationSettings.commentsEnabled(), notificationSettings2.commentsEnabled());
        assertEquals(notificationSettings.followsEnabled(), notificationSettings2.followsEnabled());
        assertEquals(notificationSettings.messagesEnabled(), notificationSettings2.messagesEnabled());
        assertEquals(notificationSettings.pushEnabled(), notificationSettings2.pushEnabled());
    }

}
