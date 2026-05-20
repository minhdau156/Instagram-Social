package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserStats;

public record UserSearchResponse(
        String id,
        String username,
        String fullName,
        String avatarUrl,
        boolean isPrivate,
        long followerCount) {
    public static UserSearchResponse from(User user, UserStats userStats) {
        return new UserSearchResponse(
                user.getId().toString(),
                user.getUsername(),
                user.getFullName(),
                user.getProfilePictureUrl(),
                user.getPrivacyLevel() == PrivacyLevel.PRIVATE,
                userStats.followerCount());
    }
}
