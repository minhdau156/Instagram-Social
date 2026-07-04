package com.instagram.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.instagram.domain.model.Notification;
import com.instagram.domain.model.NotificationSettings;
import com.instagram.domain.model.User;
import com.instagram.domain.port.in.notification.GetNotificationSettingsUseCase;
import com.instagram.domain.port.in.notification.GetNotificationsUseCase;
import com.instagram.domain.port.in.notification.MarkAllNotificationsReadUseCase;
import com.instagram.domain.port.in.notification.MarkNotificationReadUseCase;
import com.instagram.domain.port.in.notification.UpdateNotificationSettingsUseCase;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.instagram.adapter.in.web.dto.request.NotificationSettingsRequest;
import com.instagram.adapter.in.web.dto.request.RegisterDeviceTokenRequest;
import com.instagram.adapter.out.persistence.repository.DeviceTokenJpaRepository;
import com.instagram.adapter.out.persistence.repository.IdempotencyKeyJpaRepository;
import com.instagram.domain.port.in.user.GetUserUseCase;
import com.instagram.infrastructure.security.JwtTokenProvider;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;
import com.instagram.infrastructure.security.SecurityConfig;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
public class NotificationControllerIT {
    private static final String CURRENT_USER_ID = "123e4567-e89b-12d3-a456-426614174000";
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;
    @MockBean
    private IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;
    @MockBean
    private GetNotificationsUseCase getNotificationsUseCase;
    @MockBean
    private MarkAllNotificationsReadUseCase markAllNotificationsReadUseCase;
    @MockBean
    private MarkNotificationReadUseCase markNotificationReadUseCase;
    @MockBean
    private GetNotificationSettingsUseCase getNotificationSettingsUseCase;
    @MockBean
    private UpdateNotificationSettingsUseCase updateNotificationSettingsUseCase;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GetUserUseCase getUserUseCase;
    @MockBean
    private DeviceTokenJpaRepository deviceTokenJpaRepository;

    private Notification buildNotification() {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID())
                .recipientId(UUID.fromString(CURRENT_USER_ID))
                .actorId(UUID.randomUUID())
                .entityType(Notification.EntityType.POST)
                .entityId(UUID.randomUUID())
                .type(Notification.NotificationType.LIKE_POST)
                .isRead(false)
                .createdAt(OffsetDateTime.now())
                .build();
        return notification;
    }

    private NotificationSettings buildNotificationSettings() {
        return NotificationSettings.defaultsFor(UUID.fromString(CURRENT_USER_ID));
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void getNotifications_whenSuccess_shouldReturnNotifications() throws Exception {
        Notification notification = buildNotification();
        when(getNotificationsUseCase.getNotifications(any()))
                .thenReturn(List.of(notification));
        when(getUserUseCase.getUser(any()))
                .thenReturn(User.builder()
                        .id(UUID.fromString(CURRENT_USER_ID))
                        .username("test")
                        .email("test@test.com")
                        .build());
        mockMvc.perform(get("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.data.items[0].id").value(notification.getId().toString()));
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void markAllNotificationsRead_whenSuccess_shouldReturnSuccess() throws Exception {
        mockMvc.perform(put("/api/v1/notifications/read-all")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void markNotificationRead_whenSuccess_shouldReturnSuccess() throws Exception {
        Notification notification = buildNotification();
        mockMvc.perform(put("/api/v1/notifications/{id}/read", notification.getId())
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void getNotificationSettings_whenSuccess_shouldReturnSettings() throws Exception {
        NotificationSettings settings = buildNotificationSettings();
        when(getNotificationSettingsUseCase.getSettings(any()))
                .thenReturn(settings);
        mockMvc.perform(get("/api/v1/notifications/settings")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void updateNotificationSettings_whenSuccess_shouldReturnSuccess() throws Exception {
        NotificationSettingsRequest settingsRequest = new NotificationSettingsRequest(true, true, true, true, true);
        mockMvc.perform(put("/api/v1/notifications/settings")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(settingsRequest)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void createDeviceToken_whenSuccess_shouldReturnSuccess() throws Exception {
        RegisterDeviceTokenRequest request = new RegisterDeviceTokenRequest("test", "android");
        mockMvc.perform(post("/api/v1/device-tokens")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
    }

    @Test
    void getNotifications_whenNoAuth_shouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
