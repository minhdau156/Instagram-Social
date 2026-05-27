package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.model.Permission;
import com.instagram.domain.model.PermissionName;

import java.util.UUID;

public record PermissionResponse(UUID id, PermissionName name, String description) {

    public static PermissionResponse from(Permission permission) {
        return new PermissionResponse(permission.getId(), permission.getName(), permission.getDescription());
    }

    public static PermissionResponse fromName(PermissionName name) {
        return new PermissionResponse(null, name, null);
    }
}
