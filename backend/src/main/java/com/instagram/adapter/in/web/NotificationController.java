package com.instagram.adapter.in.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.adapter.in.web.dto.request.NotificationSettingsRequest;
import com.instagram.adapter.in.web.dto.request.RegisterDeviceTokenRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.NotificationResponse;
import com.instagram.adapter.out.persistence.entity.DeviceTokenJpaEntity;
import com.instagram.adapter.out.persistence.repository.DeviceTokenJpaRepository;
import com.instagram.domain.model.Notification;
import com.instagram.domain.model.NotificationSettings;
import com.instagram.domain.model.User;
import com.instagram.domain.port.in.notification.GetNotificationSettingsUseCase;
import com.instagram.domain.port.in.notification.GetNotificationsUseCase;
import com.instagram.domain.port.in.notification.MarkAllNotificationsReadUseCase;
import com.instagram.domain.port.in.notification.MarkNotificationReadUseCase;
import com.instagram.domain.port.in.notification.UpdateNotificationSettingsUseCase;
import com.instagram.domain.port.in.user.GetUserUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Notifications", description = "Notification endpoints")
public class NotificationController {
    private final GetNotificationsUseCase getNotificationsUseCase;
    private final MarkNotificationReadUseCase markNotificationReadUseCase;
    private final MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase;
    private final GetNotificationSettingsUseCase getNotificationSettingsUseCase;
    private final UpdateNotificationSettingsUseCase updateNotificationSettingsUseCase;
    private final GetUserUseCase getUserUseCase;
    private final DeviceTokenJpaRepository deviceTokenJpaRepository;

    public NotificationController(GetNotificationsUseCase getNotificationsUseCase,
            MarkNotificationReadUseCase markNotificationReadUseCase,
            MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase,
            GetNotificationSettingsUseCase getNotificationSettingsUseCase,
            UpdateNotificationSettingsUseCase updateNotificationSettingsUseCase,
            GetUserUseCase getUserUseCase,
            DeviceTokenJpaRepository deviceTokenJpaRepository) {
        this.getNotificationsUseCase = getNotificationsUseCase;
        this.markNotificationReadUseCase = markNotificationReadUseCase;
        this.markAllNotificationsReadUseCase = markAllNotificationsReadUseCase;
        this.getNotificationSettingsUseCase = getNotificationSettingsUseCase;
        this.updateNotificationSettingsUseCase = updateNotificationSettingsUseCase;
        this.getUserUseCase = getUserUseCase;
        this.deviceTokenJpaRepository = deviceTokenJpaRepository;
    }

    @Nullable
    private UUID currentUserIdOrNull() {
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return UUID.fromString(userDetails.getUsername());
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }

    private UUID currentUserId() {
        UUID userId = currentUserIdOrNull();
        if (userId == null) {
            throw new IllegalStateException("User is not authenticated");
        }
        return userId;
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        List<Notification> notifications = getNotificationsUseCase
                .getNotifications(new GetNotificationsUseCase.Query(currentUserId(), page, size));

        Map<UUID, User> actorMap = new HashMap<>();
        for (Notification notification : notifications) {
            if (notification.getActorId() != null) {
                actorMap.computeIfAbsent(notification.getActorId(),
                        id -> getUserUseCase.getUser(new GetUserUseCase.Query(id)));
            }
        }

        List<NotificationResponse> responses = notifications.stream()
                .map(notification -> NotificationResponse.from(notification, actorMap.get(notification.getActorId())))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(responses));

    }

    @PutMapping("/notifications/read-all")
    public ResponseEntity<Void> markAllNotificationsRead() {
        markAllNotificationsReadUseCase.markAllRead(new MarkAllNotificationsReadUseCase.Command(currentUserId()));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/notifications/{id}/read")
    public ResponseEntity<Void> markNotificationRead(@PathVariable("id") UUID notificationId) {
        markNotificationReadUseCase.markRead(new MarkNotificationReadUseCase.Command(notificationId, currentUserId()));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/notifications/settings")
    public ResponseEntity<ApiResponse<NotificationSettings>> getNotificationSettings() {
        UUID userId = currentUserId();
        NotificationSettings settings = getNotificationSettingsUseCase
                .getSettings(new GetNotificationSettingsUseCase.Query(userId));
        return ResponseEntity.ok(ApiResponse.ok(settings));
    }

    @PutMapping("/notifications/settings")
    public ResponseEntity<ApiResponse<NotificationSettings>> updateNotificationSettings(
            @Valid @RequestBody NotificationSettingsRequest request) {
        UUID userId = currentUserId();
        NotificationSettings updatedSettings = updateNotificationSettingsUseCase
                .updateSettings(new UpdateNotificationSettingsUseCase.Command(
                        userId,
                        request.likesEnabled(),
                        request.commentsEnabled(),
                        request.followsEnabled(),
                        request.messagesEnabled(),
                        request.pushEnabled()));
        return ResponseEntity.ok(ApiResponse.ok(updatedSettings));
    }

    @PostMapping("/device-tokens")
    public ResponseEntity<Void> registerDeviceToken(@Valid @RequestBody RegisterDeviceTokenRequest request) {
        DeviceTokenJpaEntity entity = DeviceTokenJpaEntity.builder()
                .userId(currentUserId())
                .token(request.token())
                .platform(request.platform())
                .build();
        deviceTokenJpaRepository.save(entity);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

}
