package com.instagram.adapter.in.web;

import com.instagram.adapter.in.web.dto.request.AssignRoleRequest;
import com.instagram.adapter.in.web.dto.request.UpdateRolePermissionsRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.PermissionResponse;
import com.instagram.adapter.in.web.dto.response.RoleResponse;
import com.instagram.adapter.in.web.dto.response.UserRolesResponse;
import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.Role;
import com.instagram.domain.model.RoleName;
import com.instagram.domain.port.in.rbac.AssignRoleToUserUseCase;
import com.instagram.domain.port.in.rbac.GetUserRolesUseCase;
import com.instagram.domain.port.in.rbac.ListRolesUseCase;
import com.instagram.domain.port.in.rbac.RevokeRoleFromUserUseCase;
import com.instagram.domain.port.in.rbac.UpdateRolePermissionsUseCase;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin")
@AllArgsConstructor
@Tag(name = "Admin · Roles", description = "Role and permission management endpoints")
public class RoleAdminController {

    private final ListRolesUseCase listRolesUseCase;
    private final UpdateRolePermissionsUseCase updateRolePermissionsUseCase;
    private final AssignRoleToUserUseCase assignRoleToUserUseCase;
    private final RevokeRoleFromUserUseCase revokeRoleFromUserUseCase;
    private final GetUserRolesUseCase getUserRolesUseCase;

    private UUID currentUserId() {
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            throw new IllegalStateException("User is not authenticated");
        }
        if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return UUID.fromString(userDetails.getUsername());
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }

    @GetMapping("/roles")
    public ResponseEntity<ApiResponse<List<RoleResponse>>> listRoles() {
        List<RoleResponse> roles = listRolesUseCase.listRoles(new ListRolesUseCase.Query())
                .stream()
                .map(RoleResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(roles));
    }

    @GetMapping("/permissions")
    @PreAuthorize("hasAuthority('ROLE_VIEW')")
    public ResponseEntity<ApiResponse<List<PermissionResponse>>> listPermissions() {
        List<PermissionResponse> permissions = Arrays.stream(PermissionName.values())
                .map(PermissionResponse::fromName)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(permissions));
    }

    @PutMapping("/roles/{roleName}/permissions")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRolePermissions(
            @PathVariable RoleName roleName,
            @Valid @RequestBody UpdateRolePermissionsRequest request) {
        updateRolePermissionsUseCase.updateRolePermissions(
                new UpdateRolePermissionsUseCase.Command(currentUserId(), roleName, request.permissions()));
        Role updated = listRolesUseCase.listRoles(new ListRolesUseCase.Query())
                .stream()
                .filter(r -> r.getName() == roleName)
                .findFirst()
                .orElseThrow();
        return ResponseEntity.ok(ApiResponse.ok(RoleResponse.from(updated)));
    }

    @GetMapping("/users/{id}/roles")
    public ResponseEntity<ApiResponse<UserRolesResponse>> getUserRoles(@PathVariable UUID id) {
        Set<Role> roles = getUserRolesUseCase.getUserRoles(new GetUserRolesUseCase.Query(id));
        return ResponseEntity.ok(ApiResponse.ok(UserRolesResponse.from(id, roles)));
    }

    @PostMapping("/users/{id}/roles")
    public ResponseEntity<ApiResponse<UserRolesResponse>> assignRole(
            @PathVariable UUID id,
            @Valid @RequestBody AssignRoleRequest request) {
        Set<Role> roles = assignRoleToUserUseCase.assignRoleToUser(
                new AssignRoleToUserUseCase.Command(currentUserId(), id, request.roleName()));
        return ResponseEntity.ok(ApiResponse.ok(UserRolesResponse.from(id, roles)));
    }

    @DeleteMapping("/users/{id}/roles/{roleName}")
    public ResponseEntity<Void> revokeRole(
            @PathVariable UUID id,
            @PathVariable RoleName roleName) {
        revokeRoleFromUserUseCase.revokeRoleFromUser(
                new RevokeRoleFromUserUseCase.Command(currentUserId(), id, roleName));
        return ResponseEntity.noContent().build();
    }
}
