package com.instagram.adapter.in.web.dto.request;

import com.instagram.domain.model.RoleName;
import jakarta.validation.constraints.NotNull;

public record AssignRoleRequest(@NotNull RoleName roleName) {
}
