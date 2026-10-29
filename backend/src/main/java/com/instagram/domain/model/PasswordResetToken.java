package com.instagram.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnore;

public record PasswordResetToken(UUID id, UUID userId, String tokenHash,
        OffsetDateTime expiresAt, OffsetDateTime usedAt, OffsetDateTime createdAt) {
    @JsonIgnore
    public boolean isUsed() {
        return usedAt != null;
    }

    @JsonIgnore
    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    @JsonIgnore
    public boolean isValid() {
        return !isUsed() && !isExpired();
    }
}
