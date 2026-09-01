package com.instagram.domain.port.out;

import java.util.Optional;
import java.util.UUID;

import com.instagram.domain.model.PasswordResetToken;

public interface PasswordResetTokenRepository  {
    PasswordResetToken save(PasswordResetToken token);
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);
    void markUsed(UUID tokenId);
}
