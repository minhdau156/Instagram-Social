package com.instagram.infrastructure.security;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instagram.adapter.in.web.AdminController;
import com.instagram.adapter.in.web.RoleAdminController;
import com.instagram.adapter.in.web.dto.request.AssignRoleRequest;
import com.instagram.adapter.in.web.dto.request.ReviewReportRequest;
import com.instagram.adapter.in.web.dto.request.SuspendUserRequest;
import com.instagram.application.service.AdminService;
import com.instagram.application.service.RbacService;
import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.Report;
import com.instagram.domain.model.ReportEntityType;
import com.instagram.domain.model.ReportStatus;
import com.instagram.domain.model.Role;
import com.instagram.domain.model.RoleName;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserStatus;
import com.instagram.domain.port.in.admin.ReviewReportUseCase;
import com.instagram.domain.port.in.search.SearchUsersUseCase;
import com.instagram.domain.port.in.user.FindAllUserUseCase;
import com.instagram.domain.port.in.user.GetUserUseCase;
import com.instagram.domain.port.out.AuditLogRepository;
import com.instagram.domain.port.out.ModerationRepository;
import com.instagram.domain.port.out.PermissionRepository;
import com.instagram.domain.port.out.RoleRepository;
import com.instagram.domain.port.out.UserRepository;

@WebMvcTest({AdminController.class, RoleAdminController.class})
@Import({SecurityConfig.class, AdminService.class, RbacService.class})
public class AuthorizationIT {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    // Domain ports used by AdminService and RbacService
    @MockBean
    private ModerationRepository moderationRepository;
    @MockBean
    private AuditLogRepository auditLogRepository;
    @MockBean
    private UserRepository userRepository;
    @MockBean
    private RoleRepository roleRepository;
    @MockBean
    private PermissionRepository permissionRepository;

    // Other AdminController dependencies not covered by real services
    @MockBean
    private GetUserUseCase getUserUseCase;
    @MockBean
    private FindAllUserUseCase findAllUserUseCase;
    @MockBean
    private SearchUsersUseCase searchUsersUseCase;

    UUID moderatorId = UUID.randomUUID();
    UUID regularUserId = UUID.randomUUID();
    UUID adminId = UUID.randomUUID();
    UUID targetId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // Moderator: ROLE_MODERATOR + REPORT_VIEW + REPORT_REVIEW (no USER_SUSPEND)
        when(jwtTokenProvider.validateAccessToken("moderator-token"))
                .thenReturn(Optional.of(moderatorId));
        when(roleRepository.findRolesByUserId(moderatorId))
                .thenReturn(Set.of(buildRole(RoleName.MODERATOR)));
        when(roleRepository.findPermissionNamesByUserId(moderatorId))
                .thenReturn(Set.of(PermissionName.REPORT_VIEW, PermissionName.REPORT_REVIEW));

        // Plain user: ROLE_USER, no admin-level role
        when(jwtTokenProvider.validateAccessToken("user-token"))
                .thenReturn(Optional.of(regularUserId));
        when(roleRepository.findRolesByUserId(regularUserId))
                .thenReturn(Set.of(buildRole(RoleName.USER)));
        when(roleRepository.findPermissionNamesByUserId(regularUserId))
                .thenReturn(Set.of());

        // Admin: ROLE_ADMIN + ROLE_ASSIGN (not SUPER_ADMIN, so privilege escalation guard fires)
        when(jwtTokenProvider.validateAccessToken("admin-token"))
                .thenReturn(Optional.of(adminId));
        when(roleRepository.findRolesByUserId(adminId))
                .thenReturn(Set.of(buildRole(RoleName.ADMIN)));
        when(roleRepository.findPermissionNamesByUserId(adminId))
                .thenReturn(Set.of(PermissionName.ROLE_ASSIGN));
    }

    @Test
    void moderator_canReviewReport_returns200() throws Exception {
        Report report = Report.builder()
                .id(UUID.randomUUID())
                .reporterId(UUID.randomUUID())
                .entityType(ReportEntityType.POST)
                .entityId(UUID.randomUUID())
                .reason("SPAM")
                .status(ReportStatus.PENDING)
                .createdAt(OffsetDateTime.now())
                .build();
        when(moderationRepository.findReportById(any())).thenReturn(Optional.of(report));
        when(moderationRepository.saveReport(any())).thenAnswer(inv -> inv.getArgument(0));

        User reporter = User.builder()
                .id(report.getReporterId())
                .username("reporter")
                .createdAt(OffsetDateTime.now())
                .build();
        when(getUserUseCase.getUser(any())).thenReturn(reporter);

        mockMvc.perform(put("/api/v1/admin/reports/{id}", report.getId())
                .header("Authorization", "Bearer moderator-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        new ReviewReportRequest(ReviewReportUseCase.ReviewAction.RESOLVE))))
                .andExpect(status().isOk());
    }

    @Test
    void moderator_cannotSuspendUser_returns403() throws Exception {
        mockMvc.perform(put("/api/v1/admin/users/{id}/suspend", targetId)
                .header("Authorization", "Bearer moderator-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new SuspendUserRequest("Spam"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void plainUser_onAdminEndpoint_returns403WithJsonBody() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports")
                .header("Authorization", "Bearer user-token"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void admin_assigningSuperAdminRole_returns403() throws Exception {
        when(roleRepository.findByName(RoleName.SUPER_ADMIN))
                .thenReturn(Optional.of(buildRole(RoleName.SUPER_ADMIN)));

        mockMvc.perform(post("/api/v1/admin/users/{id}/roles", targetId)
                .header("Authorization", "Bearer admin-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AssignRoleRequest(RoleName.SUPER_ADMIN))))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_onAdminEndpoint_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/admin/reports"))
                .andExpect(status().isUnauthorized());
    }

    private Role buildRole(RoleName name) {
        return Role.builder()
                .id(UUID.randomUUID())
                .name(name)
                .system(true)
                .build();
    }
}
