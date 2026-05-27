package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.model.Role;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserRolesResponse(UUID userId, Set<RoleResponse> roles) {

    public static UserRolesResponse from(UUID userId, Set<Role> roles) {
        return new UserRolesResponse(
                userId,
                roles.stream().map(RoleResponse::from).collect(Collectors.toSet()));
    }
}
