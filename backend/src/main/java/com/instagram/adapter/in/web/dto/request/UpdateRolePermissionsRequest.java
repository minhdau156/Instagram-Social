package com.instagram.adapter.in.web.dto.request;

import com.instagram.domain.model.PermissionName;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

public record UpdateRolePermissionsRequest(@NotNull Set<PermissionName> permissions) {
}
