package com.instagram.application.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.instagram.domain.exception.NotificationNotFoundException;
import com.instagram.domain.exception.UnauthorizedNotificationAccessException;
import com.instagram.domain.model.Notification;
import com.instagram.domain.model.NotificationSettings;
import com.instagram.domain.port.in.notification.CreateNotificationUseCase;
import com.instagram.domain.port.in.notification.GetNotificationSettingsUseCase;
import com.instagram.domain.port.in.notification.GetNotificationsUseCase;
import com.instagram.domain.port.in.notification.MarkAllNotificationsReadUseCase;
import com.instagram.domain.port.in.notification.MarkNotificationReadUseCase;
import com.instagram.domain.port.in.notification.UpdateNotificationSettingsUseCase;
import com.instagram.domain.port.out.NotificationRepository;
import com.instagram.domain.port.out.NotificationSettingsRepository;
import com.instagram.domain.port.out.PushNotificationPort;
import com.instagram.domain.port.out.UserRepository;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationSettingsRepository notificationSettingsRepository;
    @Mock
    private SimpMessagingTemplate simpMessagingTemplate;
    @Mock
    private PushNotificationPort pushNotificationPort;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    Notification notification;
    NotificationSettings notificationSettings;

    @BeforeEach
    void setUp() {
        notification = Notification.builder()
                .recipientId(UUID.randomUUID())
                .actorId(UUID.randomUUID())

                .entityId(UUID.randomUUID())
                .type(Notification.NotificationType.LIKE_POST)
                .isRead(false)
                .createdAt(OffsetDateTime.now())
                .build();
        notificationSettings = NotificationSettings.defaultsFor(UUID.randomUUID());
    }

    @Test
    void createNotification_shouldCreateAndSendNotification_whenSettingsAllow() {
        when(notificationSettingsRepository.findByUserId(notification.getRecipientId()))
                .thenReturn(Optional.of(notificationSettings));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);
        notificationService.createNotification(new CreateNotificationUseCase.Command(notification.getType(),
                notification.getRecipientId(), notification.getActorId(), notification.getEntityType(),
                notification.getEntityId()));
        verify(simpMessagingTemplate).convertAndSendToUser(notification.getRecipientId().toString(),
                "/topic/notifications", notification);
    }

    @Test
    void createNotification_NoSendAndCreateNotification_whenSettingsDoNotAllowLike() {
        notificationSettings = notificationSettings.withLikesEnabled(false);
        when(notificationSettingsRepository.findByUserId(notification.getRecipientId()))
                .thenReturn(Optional.of(notificationSettings));

        notificationService
                .createNotification(new CreateNotificationUseCase.Command(Notification.NotificationType.LIKE_POST,
                        notification.getRecipientId(), notification.getActorId(), notification.getEntityType(),
                        notification.getEntityId()));
        verify(simpMessagingTemplate, never()).convertAndSendToUser(anyString(), anyString(), any());
        verify(notificationRepository, never()).save(any(Notification.class));
    }

    @Test
    void getNotifications_shouldReturnNotifications_whenUserExists() {
        when(notificationRepository.findByRecipientId(any(UUID.class), isNull(), isNull(), anyInt()))
                .thenReturn(List.of(notification));
        notificationService.getNotifications(new GetNotificationsUseCase.Query(notification.getRecipientId(), null, 10));
        verify(notificationRepository).findByRecipientId(notification.getRecipientId(), null, null, 10);
    }

    @Test
    void markAllRead_shouldMarkAllNotificationsAsRead_whenUserExists() {
        notificationService.markAllRead(new MarkAllNotificationsReadUseCase.Command(notification.getRecipientId()));
        verify(notificationRepository).markAllAsRead(notification.getRecipientId());
    }

    @Test
    void markRead_shouldMarkNotificationAsRead_whenUserExists() {
        notification = Notification.builder()
                .id(UUID.randomUUID())
                .recipientId(notification.getRecipientId())
                .actorId(notification.getActorId())
                .entityId(notification.getEntityId())
                .type(notification.getType())
                .isRead(false)
                .createdAt(OffsetDateTime.now())
                .build();
        when(notificationRepository.findById(any(UUID.class)))
                .thenReturn(Optional.of(notification));
        notificationService
                .markRead(new MarkNotificationReadUseCase.Command(notification.getId(), notification.getRecipientId()));
        verify(notificationRepository).markAsRead(notification.getId());
    }

    @Test
    void markRead_shouldThrowException_whenNotificationNotFound() {
        when(notificationRepository.findById(any(UUID.class)))
                .thenReturn(Optional.empty());
        assertThrows(NotificationNotFoundException.class, () -> {
            notificationService
                    .markRead(
                            new MarkNotificationReadUseCase.Command(UUID.randomUUID(), notification.getRecipientId()));
        });
    }

    @Test
    void markRead_shouldThrowException_whenUserIsNotAuthorized() {
        when(notificationRepository.findById(any(UUID.class)))
                .thenReturn(Optional.of(notification));
        assertThrows(UnauthorizedNotificationAccessException.class, () -> {
            notificationService
                    .markRead(new MarkNotificationReadUseCase.Command(UUID.randomUUID(), UUID.randomUUID()));
        });
    }

    @Test
    void getSettings_shouldReturnSettings_whenUserSettingsExist() {
        when(notificationSettingsRepository.findByUserId(any(UUID.class)))
                .thenReturn(Optional.of(notificationSettings));
        notificationService.getSettings(new GetNotificationSettingsUseCase.Query(notification.getRecipientId()));
        verify(notificationSettingsRepository).findByUserId(notification.getRecipientId());
    }

    @Test
    void updateSettings_shouldUpdateSettings_whenUserSettingsExist() {
        when(notificationSettingsRepository.findByUserId(any(UUID.class)))
                .thenReturn(Optional.of(notificationSettings));
        when(notificationSettingsRepository.save(any(NotificationSettings.class)))
                .thenReturn(notificationSettings);
        notificationService.updateSettings(
                new UpdateNotificationSettingsUseCase.Command(notification.getRecipientId(), true, true, true,
                        true, true));
        verify(notificationSettingsRepository).save(any(NotificationSettings.class));
    }

}
