package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.RoleName;

import java.util.Set;

public record UserGrantsResponse(Set<RoleName> roles, Set<PermissionName> permissions) {
}
