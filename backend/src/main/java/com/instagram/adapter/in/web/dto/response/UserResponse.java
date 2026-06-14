package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.User;
import java.util.UUID;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Basic user details response object")
public record UserResponse(
        @Schema(description = "Unique user identifier", example = "123e4567-e89b-12d3-a456-426614174000") UUID id,

        @Schema(description = "Username", example = "john_doe") String username,

        @Schema(description = "User's email address", example = "john@example.com") String email,

        @Schema(description = "User's full name", example = "John Doe") String fullName,

        @Schema(description = "User's bio", example = "Photography enthusiast 📸") String bio,

        @Schema(description = "URL to the user's avatar image", example = "https://minio.local/avatars/123.jpg") String avatarUrl,

        @Schema(description = "Whether the user's profile is private", example = "false") boolean isPrivate,

        @Schema(description = "Whether the user is verified", example = "false") boolean isVerified

) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getBio(),
                user.getProfilePictureUrl(),
                user.getPrivacyLevel() == PrivacyLevel.PRIVATE,
                user.isVerified());

    }
}
