package com.instagram.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public record PasswordResetToken(UUID id, UUID userId, String tokenHash,
        OffsetDateTime expiresAt, OffsetDateTime usedAt, OffsetDateTime createdAt) {
    public boolean isUsed() {
        return usedAt != null;
    } 

    public boolean isExpired() {
        return OffsetDateTime.now().isAfter(expiresAt);
    }

    public boolean isValid() {
        return !isUsed() && !isExpired();
    }
}
