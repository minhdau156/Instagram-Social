package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.model.User;

public record AdminUserResponse(
        String id,
        String username,
        String email,
        String fullName,
        String accountStatus,
        boolean isVerified,
        String createdAt,
        String lastLoginAt) {

    public static AdminUserResponse from(User user) {
        return new AdminUserResponse(
                user.getId().toString(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus() != null ? user.getStatus().name() : null,
                user.isVerified(),
                user.getCreatedAt().toString(),
                user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null);
    }
}
