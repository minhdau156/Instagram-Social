package com.instagram.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;
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
import com.instagram.adapter.in.web.dto.request.AssignRoleRequest;
import com.instagram.adapter.in.web.dto.request.UpdateRolePermissionsRequest;
import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.Role;
import com.instagram.domain.model.RoleName;
import com.instagram.domain.port.in.rbac.AssignRoleToUserUseCase;
import com.instagram.domain.port.in.rbac.GetUserRolesUseCase;
import com.instagram.domain.port.in.rbac.ListRolesUseCase;
import com.instagram.domain.port.in.rbac.RevokeRoleFromUserUseCase;
import com.instagram.domain.port.in.rbac.UpdateRolePermissionsUseCase;
import com.instagram.infrastructure.security.JwtTokenProvider;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;
import com.instagram.infrastructure.security.SecurityConfig;

@WebMvcTest(RoleAdminController.class)
@Import(SecurityConfig.class)
public class RoleAdminControllerTest {

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

    @MockBean
    private ListRolesUseCase listRolesUseCase;
    @MockBean
    private UpdateRolePermissionsUseCase updateRolePermissionsUseCase;
    @MockBean
    private AssignRoleToUserUseCase assignRoleToUserUseCase;
    @MockBean
    private RevokeRoleFromUserUseCase revokeRoleFromUserUseCase;
    @MockBean
    private GetUserRolesUseCase getUserRolesUseCase;

    private final String CURRENT_USER_ID = "550e8400-e29b-41d4-a716-446655440000";

    private Role buildRole(RoleName name) {
        return Role.builder()
                .id(UUID.randomUUID())
                .name(name)
                .system(true)
                .build();
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID, authorities = {"ROLE_ADMIN", "ROLE_VIEW"})
    void listRoles_whenPrincipalHasRoleView_returns200() throws Exception {
        when(listRolesUseCase.listRoles(any())).thenReturn(List.of(buildRole(RoleName.MODERATOR)));

        mockMvc.perform(get("/api/v1/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID, roles = "USER")
    void listRoles_whenPrincipalLacksAdminRole_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roles"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID, authorities = {"ROLE_SUPER_ADMIN", "ROLE_PERMISSION_MANAGE"})
    void updateRolePermissions_whenPrincipalHasRolePermissionManage_returns200() throws Exception {
        Role moderatorRole = buildRole(RoleName.MODERATOR);
        when(listRolesUseCase.listRoles(any())).thenReturn(List.of(moderatorRole));

        UpdateRolePermissionsRequest request =
                new UpdateRolePermissionsRequest(Set.of(PermissionName.REPORT_VIEW, PermissionName.ROLE_PERMISSION_MANAGE));

        mockMvc.perform(put("/api/v1/admin/roles/{roleName}/permissions", RoleName.MODERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID, roles = "USER")
    void updateRolePermissions_whenPrincipalLacksAdminRole_returns403() throws Exception {
        UpdateRolePermissionsRequest request =
                new UpdateRolePermissionsRequest(Set.of(PermissionName.REPORT_VIEW));

        mockMvc.perform(put("/api/v1/admin/roles/{roleName}/permissions", RoleName.MODERATOR)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = CURRENT_USER_ID, authorities = {"ROLE_ADMIN", "ROLE_ASSIGN"})
    void assignRole_whenPrincipalHasRoleAssign_returns200() throws Exception {
        UUID targetId = UUID.randomUUID();
        when(assignRoleToUserUseCase.assignRoleToUser(any())).thenReturn(Set.of(buildRole(RoleName.MODERATOR)));

        mockMvc.perform(post("/api/v1/admin/users/{id}/roles", targetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AssignRoleRequest(RoleName.MODERATOR))))
                .andExpect(status().isOk());
    }

    @Test
    void assignRole_whenUnauthenticated_returns401() throws Exception {
        UUID targetId = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/admin/users/{id}/roles", targetId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AssignRoleRequest(RoleName.MODERATOR))))
                .andExpect(status().isUnauthorized());
    }
}
