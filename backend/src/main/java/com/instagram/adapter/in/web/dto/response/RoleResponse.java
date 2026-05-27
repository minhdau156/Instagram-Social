package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.Role;
import com.instagram.domain.model.RoleName;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record RoleResponse(UUID id, RoleName name, String description, boolean system, Set<PermissionName> permissions) {

    public static RoleResponse from(Role role) {
        Set<PermissionName> permNames = role.getPermissions().stream()
                .map(p -> p.getName())
                .collect(Collectors.toSet());
        return new RoleResponse(role.getId(), role.getName(), role.getDescription(), role.isSystem(), permNames);
    }
}
