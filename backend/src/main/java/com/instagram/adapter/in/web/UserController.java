package com.instagram.adapter.in.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;
import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.RoleName;
import com.instagram.domain.model.User;
import com.instagram.domain.port.in.ExportUserDataUseCase;
import com.instagram.domain.port.in.GetUserProfileUseCase;
import com.instagram.domain.port.in.UpdateProfileUseCase;
import com.instagram.domain.port.in.rbac.GetUserPermissionsUseCase;
import com.instagram.domain.port.in.rbac.GetUserRolesUseCase;
import com.instagram.domain.port.in.search.SearchUsersUseCase;
import com.instagram.domain.port.in.user.GetUserUseCase;

import com.instagram.domain.port.out.MediaStoragePort;
import com.instagram.infrastructure.security.HtmlSanitizer;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.annotation.Nullable;
import jakarta.validation.Valid;

import com.instagram.adapter.in.web.dto.request.UpdateProfileRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.UserGrantsResponse;
import com.instagram.adapter.in.web.dto.response.UserProfileResponse;
import com.instagram.adapter.in.web.dto.response.UserResponse;

import java.util.List;
import java.util.Set;
import com.instagram.domain.model.UserProfile;
import com.instagram.domain.model.PrivacyLevel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "User Management", description = "Endpoints for user profile and avatar management")
public class UserController {

    private final GetUserProfileUseCase getUserProfileUseCase;
    private final UpdateProfileUseCase updateProfileUseCase;
    private final MediaStoragePort mediaStoragePort;
    private final GetUserUseCase getUserUseCase;
    private final SearchUsersUseCase searchUsersUseCase;
    private final GetUserRolesUseCase getUserRolesUseCase;
    private final GetUserPermissionsUseCase getUserPermissionsUseCase;
    private final ExportUserDataUseCase exportUserDataUseCase;
    private final HtmlSanitizer htmlSanitizer;

    @Operation(summary = "Get Current User Profile", description = "Retrieves the profile of the currently authenticated user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved profile"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/profile/get")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile() {
        log.debug("getMyProfile");
        UserProfile profile = getUserProfileUseCase
                .getUserProfile(new GetUserProfileUseCase.Query(null, currentUserId()));
        return ResponseEntity.ok(ApiResponse.ok(UserProfileResponse.from(profile)));
    }

    @Operation(summary = "Update Current User Profile", description = "Updates the bio, full name, or privacy level of the current user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully updated profile"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @PutMapping("/profile/update")
    public ResponseEntity<ApiResponse<UserResponse>> updateMyProfile(@Valid @RequestBody UpdateProfileRequest req) {
        PrivacyLevel privacyLevel = req.isPrivate() != null
                ? (req.isPrivate() ? PrivacyLevel.PRIVATE : PrivacyLevel.PUBLIC)
                : null;
        User user = updateProfileUseCase.updateProfile(new UpdateProfileUseCase.Command(
                currentUserId().toString(), htmlSanitizer.sanitize(req.fullName()), htmlSanitizer.sanitize(req.bio()),
                null, null, privacyLevel));
        log.info("Profile updated");
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }

    @Operation(summary = "Upload Avatar", description = "Uploads a new avatar for the authenticated user")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully uploaded and updated avatar"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file format"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Internal server error reading/uploading file")
    })
    @PutMapping(value = "/profile/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.equals("image/jpeg") && !contentType.equals("image/png")
                && !contentType.equals("image/webp"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid file format. Only JPEG, PNG, and WEBP are allowed.");
        }

        String ext = "";
        if ("image/jpeg".equals(contentType))
            ext = ".jpg";
        else if ("image/png".equals(contentType))
            ext = ".png";
        else if ("image/webp".equals(contentType))
            ext = ".webp";

        String key = "avatars/" + currentUserId() + "/" + UUID.randomUUID() + ext;
        log.info("Avatar upload initiated contentType={}", contentType);

        try {
            String avatarUrl = mediaStoragePort.uploadFile(key, file.getBytes(), contentType);
            User user = updateProfileUseCase.updateProfile(new UpdateProfileUseCase.Command(
                    currentUserId().toString(), null, null, null, avatarUrl, null));
            return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file", e);
        }
    }

    @Operation(summary = "Get User Profile", description = "Retrieves the public or authorized profile details of another user by username")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Successfully retrieved profile"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Unauthorized"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "User not found")
    })
    @GetMapping("/{username}/bio")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(@PathVariable String username) {
        log.debug("getProfile username={}", username);
        UserProfile profile = getUserProfileUseCase
                .getUserProfile(new GetUserProfileUseCase.Query(username, currentUserId()));
        return ResponseEntity.ok(ApiResponse.ok(UserProfileResponse.from(profile)));
    }

    @GetMapping("/get/{id}")
    public ResponseEntity<ApiResponse<UserResponse>> getUserById(@PathVariable UUID id) {
        log.debug("getUserById id={}", id);
        User user = getUserUseCase.getUser(new GetUserUseCase.Query(id));
        return ResponseEntity.ok(ApiResponse.ok(UserResponse.from(user)));
    }

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<List<UserResponse>>> searchUsers(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit) {
        UUID userId = currentUserId();
        log.debug("searchUsers q={} page={} limit={}", q, page, limit);
        List<UserResponse> results = searchUsersUseCase
                .searchUsers(new SearchUsersUseCase.Query(q, userId, page, limit))
                .stream()
                .map(UserResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    @GetMapping("/me/permissions")
    public ResponseEntity<ApiResponse<UserGrantsResponse>> getMyGrants() {
        log.debug("getMyGrants");
        UUID userId = currentUserId();
        Set<RoleName> roles = getUserRolesUseCase.getUserRoles(new GetUserRolesUseCase.Query(userId))
                .stream()
                .map(u -> u.getName())
                .collect(java.util.stream.Collectors.toSet());
        Set<PermissionName> permissions = getUserPermissionsUseCase
                .getUserPermissions(new GetUserPermissionsUseCase.Query(userId));
        return ResponseEntity.ok(ApiResponse.ok(new UserGrantsResponse(roles, permissions)));
    }

    @GetMapping("/me/export")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Export all posts as a CSV file download")
    public ResponseEntity<StreamingResponseBody> exportMyData(
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        log.info("User data export started userId={}", userId);
        StreamingResponseBody body = outputStream -> exportUserDataUseCase.exportPostsToCsv(
                new ExportUserDataUseCase.Command(userId, outputStream));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"posts-export.csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
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

}
