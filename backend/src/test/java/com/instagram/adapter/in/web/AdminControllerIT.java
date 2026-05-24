package com.instagram.adapter.in.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instagram.adapter.in.web.dto.request.ReviewReportRequest;
import com.instagram.adapter.in.web.dto.request.SuspendUserRequest;
import com.instagram.domain.model.Report;
import com.instagram.domain.model.ReportEntityType;
import com.instagram.domain.model.ReportStatus;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserStatus;
import com.instagram.domain.port.in.admin.AdminGetReportsUseCase;
import com.instagram.domain.port.in.admin.ReviewReportUseCase;
import com.instagram.domain.port.in.admin.SuspendUserUseCase;
import com.instagram.domain.port.in.admin.UnsuspendUserUseCase;
import com.instagram.domain.port.in.search.SearchUsersUseCase;
import com.instagram.domain.port.in.user.FindAllUserUseCase;
import com.instagram.domain.port.in.user.GetUserUseCase;
import com.instagram.infrastructure.security.JwtTokenProvider;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;
import com.instagram.infrastructure.security.SecurityConfig;

@WebMvcTest(AdminController.class)
@Import(SecurityConfig.class)
public class AdminControllerIT {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;
    @Autowired
    ObjectMapper objectMapper;
    @MockBean
    private ReviewReportUseCase reviewReportUseCase;
    @MockBean
    private SuspendUserUseCase suspendUserUseCase;
    @MockBean
    private UnsuspendUserUseCase unsuspendUserUseCase;
    @MockBean
    private AdminGetReportsUseCase adminGetReportsUseCase;
    @MockBean
    private FindAllUserUseCase findAllUserUseCase;
    @MockBean
    private GetUserUseCase getUserUseCase;
    @MockBean
    private SearchUsersUseCase searchUsersUseCase;

    private final String CURRENT_USER_ID = "550e8400-e29b-41d4-a716-446655440000";
    private final String REPORTER_ID = "550e8400-e29b-41d4-a716-446655440001";
    private final String TARGET_ID = "550e8400-e29b-41d4-a716-446655440002";

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

    private User buildUser(UUID userId, String username, UserStatus status) {
        return User.builder()
                .id(userId)
                .username(username)
                .status(status)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void getReports_whenUnauthorized_return401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID, roles = "ADMIN")
    void getReports_whenStatusFilter_passesFilterToUseCase() throws Exception {
        when(adminGetReportsUseCase.getReports(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/admin/reports")
                .param("status", "PENDING"))
                .andExpect(status().isOk());

        ArgumentCaptor<AdminGetReportsUseCase.Query> captor =
                ArgumentCaptor.forClass(AdminGetReportsUseCase.Query.class);
        verify(adminGetReportsUseCase).getReports(captor.capture());
        assertEquals(ReportStatus.PENDING, captor.getValue().status());
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID, roles = "ADMIN")
    void reviewReport_whenMissingAction_return400() throws Exception {
        mockMvc.perform(put("/api/v1/admin/reports/{id}", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID, roles = "ADMIN")
    void getReports_whenAdminUser_returnReportList() throws Exception {
        List<Report> reports = List.of(
                buildReportDomain(UUID.fromString(REPORTER_ID), "Spam", ReportStatus.PENDING),
                buildReportDomain(UUID.fromString(REPORTER_ID), "Hate Speech", ReportStatus.PENDING));
        List<User> users = List.of(
                buildUser(UUID.fromString(REPORTER_ID), "reporter_user", UserStatus.ACTIVE));

        when(adminGetReportsUseCase.getReports(any()))
                .thenReturn(reports);
        when(findAllUserUseCase.findAllByIds(any())).thenReturn(users);

        mockMvc.perform(get("/api/v1/admin/reports")
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()").value(2));

    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID, roles = "ADMIN")
    void reviewReport_whenValid_returnUpdatedReport() throws Exception {
        Report report = buildReportDomain(UUID.fromString(REPORTER_ID), "Spam", ReportStatus.PENDING);
        User user = buildUser(UUID.fromString(REPORTER_ID), "reporter_user", UserStatus.ACTIVE);
        when(reviewReportUseCase.reviewReport(any(ReviewReportUseCase.Command.class)))
                .thenReturn(report);
        when(getUserUseCase.getUser(any())).thenReturn(user);
        mockMvc.perform(put("/api/v1/admin/reports/{id}", report.getId())
                .contentType("application/json")
                .content(objectMapper
                        .writeValueAsString(new ReviewReportRequest(ReviewReportUseCase.ReviewAction.RESOLVE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID, roles = "ADMIN")
    void suspendUser_whenValid_returnSuspendedUser() throws Exception {
        User user = buildUser(UUID.fromString(TARGET_ID), "target_user", UserStatus.ACTIVE);
        when(suspendUserUseCase.suspendUser(any(SuspendUserUseCase.Command.class)))
                .thenReturn(user);
        mockMvc.perform(put("/api/v1/admin/users/{id}/suspend", user.getId())
                .contentType("application/json")
                .content(objectMapper.writeValueAsString(new SuspendUserRequest("Spam"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID, roles = "ADMIN")
    void unsuspendUser_whenValid_returnUnsuspendedUser() throws Exception {
        User user = buildUser(UUID.fromString(TARGET_ID), "target_user", UserStatus.SUSPENDED);
        when(unsuspendUserUseCase.unsuspendUser(any(UnsuspendUserUseCase.Command.class)))
                .thenReturn(user);
        mockMvc.perform(put("/api/v1/admin/users/{id}/unsuspend", user.getId())
                .contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID, roles = "ADMIN")
    void searchUsers_whenValid_returnUserList() throws Exception {
        List<User> users = List.of(buildUser(UUID.fromString(TARGET_ID), "target_user", UserStatus.ACTIVE));
        when(searchUsersUseCase.searchUsers(any(SearchUsersUseCase.Query.class)))
                .thenReturn(users);
        mockMvc.perform(get("/api/v1/admin/users")
                .contentType("application/json")
                .param("username", "target_user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size()").value(1));
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID, roles = "USER")
    void searchUsers_whenNotAdmin_return403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                .contentType("application/json")
                .param("username", "target_user"))
                .andExpect(status().isForbidden());
    }
}
