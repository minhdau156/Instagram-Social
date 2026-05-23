package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.model.User;
import com.instagram.domain.model.UserBlock;

public record BlockedUserResponse(
        String blockedUserId,
        String username,
        String fullName,
        String avatarUrl,
        String blockedAt) {

    public static BlockedUserResponse from(UserBlock block, User blockedUser) {
        return new BlockedUserResponse(
                block.getBlockedId().toString(),
                blockedUser.getUsername(),
                blockedUser.getFullName(),
                blockedUser.getProfilePictureUrl(),
                block.getCreatedAt().toString());
    }
}
