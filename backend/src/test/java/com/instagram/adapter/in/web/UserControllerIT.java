package com.instagram.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instagram.adapter.in.web.dto.request.UpdateProfileRequest;
import com.instagram.adapter.out.persistence.repository.IdempotencyKeyJpaRepository;
import com.instagram.domain.model.FollowStatus;
import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.Role;
import com.instagram.domain.model.RoleName;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserProfile;
import com.instagram.domain.model.UserStats;
import com.instagram.domain.port.in.ExportUserDataUseCase;
import com.instagram.domain.port.in.GetUserProfileUseCase;
import com.instagram.domain.port.in.UpdateProfileUseCase;
import com.instagram.domain.port.in.rbac.GetUserPermissionsUseCase;
import com.instagram.domain.port.in.rbac.GetUserRolesUseCase;
import com.instagram.domain.port.in.search.SearchUsersUseCase;
import com.instagram.domain.port.in.user.GetUserUseCase;
import com.instagram.domain.port.out.MediaStoragePort;
import com.instagram.infrastructure.security.HtmlSanitizer;
import com.instagram.infrastructure.security.JwtTokenProvider;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;
import com.instagram.infrastructure.security.SecurityConfig;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
public class UserControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    UserDetailsService userDetailsService;

    @MockBean
    OAuth2SuccessHandler oAuth2SuccessHandler;

    @MockBean
    HtmlSanitizer htmlSanitizer;

    @MockBean
    IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;

    @MockBean
    GetUserProfileUseCase getUserProfileUseCase;

    @MockBean
    UpdateProfileUseCase updateProfileUseCase;

    @MockBean
    MediaStoragePort mediaStoragePort;

    @MockBean
    GetUserUseCase getUserUseCase;

    @MockBean
    SearchUsersUseCase searchUsersUseCase;

    @MockBean
    GetUserRolesUseCase getUserRolesUseCase;

    @MockBean
    GetUserPermissionsUseCase getUserPermissionsUseCase;

    @MockBean
    ExportUserDataUseCase exportUserDataUseCase;

    private static final UUID USER_ID = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");

    private User buildUser() {
        return User.builder()
                .id(USER_ID)
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User")
                .bio("Test bio")
                .privacyLevel(PrivacyLevel.PUBLIC)
                .build();
    }

    private UserProfile buildUserProfile(User user) {
        return new UserProfile(user, UserStats.zero(user.getId()), false, FollowStatus.ACCEPTED);
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void getMyProfile_returns200_onSuccess() throws Exception {
        User user = buildUser();
        when(getUserProfileUseCase.getUserProfile(any(GetUserProfileUseCase.Query.class)))
                .thenReturn(buildUserProfile(user));

        mockMvc.perform(get("/api/v1/users/profile/get"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data.user.username").value("testuser"));
    }

    @Test
    void getMyProfile_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/profile/get"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void updateMyProfile_returns200_onSuccess() throws Exception {
        var request = new UpdateProfileRequest("New Name", "New bio", false);
        User updated = buildUser();

        when(htmlSanitizer.sanitize(any())).thenAnswer(inv -> inv.getArgument(0));
        when(updateProfileUseCase.updateProfile(any(UpdateProfileUseCase.Command.class))).thenReturn(updated);

        mockMvc.perform(put("/api/v1/users/profile/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(USER_ID.toString()));
    }

    @Test
    void updateMyProfile_unauthenticated_returns401() throws Exception {
        var request = new UpdateProfileRequest("New Name", "New bio", false);

        mockMvc.perform(put("/api/v1/users/profile/update")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void uploadAvatar_validJpeg_returns200() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});
        User user = buildUser();

        when(mediaStoragePort.uploadFile(any(), any(), any())).thenReturn("https://minio.local/avatars/avatar.jpg");
        when(updateProfileUseCase.updateProfile(any(UpdateProfileUseCase.Command.class))).thenReturn(user);

        mockMvc.perform(multipart("/api/v1/users/profile/avatar")
                .file(file)
                .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(USER_ID.toString()));
    }



    @Test
    void uploadAvatar_unauthenticated_returns401() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/v1/users/profile/avatar")
                .file(file)
                .with(req -> { req.setMethod("PUT"); return req; }))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void getProfile_byUsername_returns200_onSuccess() throws Exception {
        User user = buildUser();
        when(getUserProfileUseCase.getUserProfile(any(GetUserProfileUseCase.Query.class)))
                .thenReturn(buildUserProfile(user));

        mockMvc.perform(get("/api/v1/users/{username}/bio", "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("testuser"));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void getUserById_returns200_onSuccess() throws Exception {
        User user = buildUser();
        when(getUserUseCase.getUser(any(GetUserUseCase.Query.class))).thenReturn(user);

        mockMvc.perform(get("/api/v1/users/get/{id}", USER_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(USER_ID.toString()));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void searchUsers_returns200_withResults() throws Exception {
        User user = buildUser();
        when(searchUsersUseCase.searchUsers(any(SearchUsersUseCase.Query.class)))
                .thenReturn(List.of(user));

        mockMvc.perform(get("/api/v1/users/search").param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(USER_ID.toString()))
                .andExpect(jsonPath("$.data[0].username").value("testuser"));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void getMyGrants_returns200_withRolesAndPermissions() throws Exception {
        Role role = Role.builder().name(RoleName.USER).build();
        when(getUserRolesUseCase.getUserRoles(any(GetUserRolesUseCase.Query.class)))
                .thenReturn(Set.of(role));
        when(getUserPermissionsUseCase.getUserPermissions(any(GetUserPermissionsUseCase.Query.class)))
                .thenReturn(Set.of(PermissionName.REPORT_VIEW));

        mockMvc.perform(get("/api/v1/users/me/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles").isArray())
                .andExpect(jsonPath("$.data.permissions").isArray());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void exportMyData_returns200_withCsvContentDisposition() throws Exception {
        doNothing().when(exportUserDataUseCase).exportPostsToCsv(any(ExportUserDataUseCase.Command.class));

        mockMvc.perform(get("/api/v1/users/me/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"posts-export.csv\""));
    }

    @Test
    void exportMyData_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/export"))
                .andExpect(status().isUnauthorized());
    }
}
