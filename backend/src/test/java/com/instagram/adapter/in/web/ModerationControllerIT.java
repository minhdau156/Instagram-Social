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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instagram.adapter.in.web.dto.request.ReportRequest;
import com.instagram.adapter.out.persistence.repository.IdempotencyKeyJpaRepository;
import com.instagram.application.service.AdminService;
import com.instagram.application.service.ModerationService;
import com.instagram.domain.model.Report;
import com.instagram.domain.model.ReportEntityType;
import com.instagram.domain.model.ReportReason;
import com.instagram.domain.model.ReportStatus;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserBlock;
import com.instagram.domain.model.UserStatus;
import com.instagram.domain.port.in.moderation.BlockUserUseCase;
import com.instagram.domain.port.in.moderation.GetBlockedUsersUseCase;
import com.instagram.domain.port.in.moderation.ReportContentUseCase;
import com.instagram.domain.port.in.moderation.UnblockUserUseCase;
import com.instagram.domain.port.in.user.FindAllUserUseCase;
import com.instagram.domain.port.in.user.GetUserUseCase;
import com.instagram.infrastructure.security.JwtTokenProvider;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;
import com.instagram.infrastructure.security.SecurityConfig;

@WebMvcTest(ModerationController.class)
@Import(SecurityConfig.class)
public class ModerationControllerIT {
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
        private ReportContentUseCase reportContentUseCase;
        @MockBean
        private BlockUserUseCase blockUserUseCase;
        @MockBean
        private UnblockUserUseCase unblockUserUseCase;
        @MockBean
        private GetBlockedUsersUseCase getBlockedUsersUseCase;
        @MockBean
        private GetUserUseCase getUserUseCase;
        @MockBean
        private FindAllUserUseCase findAllUserUseCase;

        @Autowired
        ObjectMapper objectMapper;

        private final String CURRENT_USER_ID = "550e8400-e29b-41d4-a716-446655440000";

        private Report buildReportDomain(UUID reporterId, String reason, ReportStatus status) {
                return Report.builder()
                                .id(UUID.randomUUID())
                                .reporterId(reporterId)
                                .entityType(ReportEntityType.POST)
                                .entityId(UUID.randomUUID())
                                .reason(reason)
                                .details("Spam post")
                                .status(status)
                                .reviewedById(UUID.randomUUID())
                                .reviewedAt(OffsetDateTime.now())
                                .createdAt(OffsetDateTime.now())
                                .build();
        }

        private UserBlock buildUserBlock(UUID blockerId, UUID blockedId) {
                return UserBlock.builder()
                                .blockerId(blockerId)
                                .blockedId(blockedId)
                                .createdAt(OffsetDateTime.now())
                                .build();
        }

        private User buildUser(UUID userId, String username, UserStatus status) {
                return User.builder()
                                .id(userId)
                                .username(username)
                                .status(status)
                                .createdAt(OffsetDateTime.now())
                                .build();
        }

        @Test
        @WithMockUser(username = CURRENT_USER_ID, roles = "USER")
        void reportContent_whenValid_return200() throws Exception {
                Report report = buildReportDomain(UUID.fromString(CURRENT_USER_ID), "Spam", ReportStatus.PENDING);
                User user = buildUser(UUID.fromString(CURRENT_USER_ID), "testuser", UserStatus.ACTIVE);
                when(reportContentUseCase.reportContent(any(ReportContentUseCase.Command.class)))
                                .thenReturn(report);
                when(getUserUseCase.getUser(any())).thenReturn(user);
                mockMvc.perform(post("/api/v1/reports")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(new ReportRequest(
                                                ReportEntityType.POST, UUID.randomUUID(), ReportReason.SPAM,
                                                "Spam post"))))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.reporterId").value(CURRENT_USER_ID))
                                .andExpect(jsonPath("$.data").exists());
        }

        @Test
        @WithMockUser(username = CURRENT_USER_ID, roles = "USER")
        void blockUser_whenValid_return204() throws Exception {
                mockMvc.perform(post("/api/v1/users/{username}/block", "testuser"))
                                .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(username = CURRENT_USER_ID, roles = "USER")
        void unblockUser_whenValid_return204() throws Exception {
                mockMvc.perform(delete("/api/v1/users/{username}/block", "testuser"))
                                .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(username = CURRENT_USER_ID, roles = "USER")
        void getBlockedUsers_whenValid_returnBlockedUsersList() throws Exception {

                UserBlock userBlock = buildUserBlock(UUID.fromString(CURRENT_USER_ID),
                                UUID.fromString("550e8400-e29b-41d4-a716-446655440001"));
                List<User> blockedUsersList = List
                                .of(buildUser(UUID.fromString("550e8400-e29b-41d4-a716-446655440001"), "testuser",
                                                UserStatus.ACTIVE));
                when(findAllUserUseCase.findAllByIds(any())).thenReturn(blockedUsersList);
                when(getBlockedUsersUseCase.getBlockedUsers(any(GetBlockedUsersUseCase.Query.class)))
                                .thenReturn(List.of(userBlock));
                mockMvc.perform(get("/api/v1/users/me/blocked"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").exists())
                                .andExpect(jsonPath("$.data[0].blockedUserId")
                                                .value("550e8400-e29b-41d4-a716-446655440001"))
                                .andExpect(jsonPath("$.data[0].username").value("testuser"));
        }

        @Test
        @WithMockUser(username = CURRENT_USER_ID, roles = "USER")
        void reportContent_whenMissingRequiredFields_return400() throws Exception {
                mockMvc.perform(post("/api/v1/reports")
                                .contentType("application/json")
                                .content("{\"details\": \"some details\"}"))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void reportContent_whenUnauthorized_return401() throws Exception {
                mockMvc.perform(post("/api/v1/reports")
                                .contentType("application/json")
                                .content(objectMapper.writeValueAsString(new ReportRequest(
                                                ReportEntityType.POST, UUID.randomUUID(), ReportReason.SPAM,
                                                "Spam post"))))
                                .andExpect(status().isUnauthorized());
        }

}
