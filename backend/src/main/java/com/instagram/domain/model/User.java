package com.instagram.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class User {
    private UUID id;
    private String username;
    private String email;
    private String phoneNumber; // nullable
    private String passwordHash; // nullable (OAuth2 users have none)
    private String fullName;
    private String bio; // nullable
    private String profilePictureUrl;
    private String websiteUrl;
    private UserStatus status; // nullable
    private PrivacyLevel privacyLevel;
    private boolean isVerified;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime lastLoginAt;
    private UserRole role;

    public User() {

    }

    public UUID getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFullName() {
        return fullName;
    }

    public String getBio() {
        return bio;
    }

    public String getProfilePictureUrl() {
        return profilePictureUrl;
    }

    public String getWebsiteUrl() {
        return websiteUrl;
    }

    public UserStatus getStatus() {
        return status;
    }

    public PrivacyLevel getPrivacyLevel() {
        return privacyLevel;
    }

    public boolean isVerified() {
        return isVerified;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getLastLoginAt() {
        return lastLoginAt;
    }

    public UserRole getRole() {
        return role;
    }

    private Builder copy() {
        return User.builder()
                .id(this.id)
                .username(this.username)
                .email(this.email)
                .phoneNumber(this.phoneNumber)
                .passwordHash(this.passwordHash)
                .fullName(this.fullName)
                .bio(this.bio)
                .profilePictureUrl(this.profilePictureUrl)
                .websiteUrl(this.websiteUrl)
                .status(this.status)
                .privacyLevel(this.privacyLevel)
                .isVerified(this.isVerified)
                .createdAt(this.createdAt)
                .updatedAt(this.updatedAt)
                .role(this.role)
                .lastLoginAt(this.lastLoginAt);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final User user = new User();

        public Builder id(UUID id) {
            user.id = id;
            return this;
        }

        public Builder username(String username) {
            user.username = username;
            return this;
        }

        public Builder email(String email) {
            user.email = email;
            return this;
        }

        public Builder phoneNumber(String phoneNumber) {
            user.phoneNumber = phoneNumber;
            return this;
        }

        public Builder passwordHash(String passwordHash) {
            user.passwordHash = passwordHash;
            return this;
        }

        public Builder fullName(String fullName) {
            user.fullName = fullName;
            return this;
        }

        public Builder bio(String bio) {
            user.bio = bio;
            return this;
        }

        public Builder profilePictureUrl(String profilePictureUrl) {
            user.profilePictureUrl = profilePictureUrl;
            return this;
        }

        public Builder websiteUrl(String websiteUrl) {
            user.websiteUrl = websiteUrl;
            return this;
        }

        public Builder status(UserStatus status) {
            user.status = status;
            return this;
        }

        public Builder privacyLevel(PrivacyLevel privacyLevel) {
            user.privacyLevel = privacyLevel;
            return this;
        }

        public Builder isVerified(boolean isVerified) {
            user.isVerified = isVerified;
            return this;
        }

        public Builder createdAt(OffsetDateTime createdAt) {
            user.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(OffsetDateTime updatedAt) {
            user.updatedAt = updatedAt;
            return this;
        }

        public Builder lastLoginAt(OffsetDateTime lastLoginAt) {
            user.lastLoginAt = lastLoginAt;
            return this;
        }

        public Builder role(UserRole role) {
            user.role = role;
            return this;
        }

        public User build() {
            return user;
        }
    }

    public User withUpdateProfile(String fullName, PrivacyLevel privacyLevel, String websiteUrl,
            String profilePictureUrl) {
        return this.copy()
                .fullName(fullName != null ? fullName : this.fullName)
                .privacyLevel(privacyLevel != null ? privacyLevel : this.privacyLevel)
                .websiteUrl(websiteUrl != null ? websiteUrl : this.websiteUrl)
                .profilePictureUrl(profilePictureUrl != null ? profilePictureUrl : this.profilePictureUrl)
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    public User withUpdatedProfile(String fullName, String bio, String websiteUrl,
            String profilePictureUrl, PrivacyLevel privacyLevel) {
        return this.copy()
                .fullName(fullName != null ? fullName : this.fullName)
                .bio(bio != null ? bio : this.bio)
                .websiteUrl(websiteUrl != null ? websiteUrl : this.websiteUrl)
                .profilePictureUrl(profilePictureUrl != null ? profilePictureUrl : this.profilePictureUrl)
                .privacyLevel(privacyLevel != null ? privacyLevel : this.privacyLevel)
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    public User withUpdatedPasswordHash(String newPasswordHash) {
        return this.copy().passwordHash(newPasswordHash).updatedAt(OffsetDateTime.now()).build();
    }

    public User withSuspend() {
        return this.copy().status(UserStatus.SUSPENDED).updatedAt(OffsetDateTime.now()).build();
    }

    public User withUnsuspend() {
        return this.copy().status(UserStatus.ACTIVE).updatedAt(OffsetDateTime.now()).build();
    }

    public User withDeactivated() {
        return this.copy().status(UserStatus.DEACTIVATED).updatedAt(OffsetDateTime.now()).build();
    }

    public User withProfilePictureUrl(String profilePictureUrl) {
        return this.copy().profilePictureUrl(profilePictureUrl).updatedAt(OffsetDateTime.now()).build();
    }

    public boolean isActive() {
        return this.status == UserStatus.ACTIVE;
    }

}