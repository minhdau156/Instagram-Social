package com.instagram.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.PasswordResetTokenJpaEntity;
import com.instagram.adapter.out.persistence.repository.PasswordResetTokenJpaRepository;
import com.instagram.domain.model.PasswordResetToken;
import com.instagram.domain.port.out.PasswordResetTokenRepository;

@Component
public class PasswordResetTokenPersistenceAdapter implements PasswordResetTokenRepository {
    private final PasswordResetTokenJpaRepository jpaRepository;
    public PasswordResetTokenPersistenceAdapter(PasswordResetTokenJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }
    @Override
    public PasswordResetToken save(PasswordResetToken token) {
        PasswordResetTokenJpaEntity entity = toEntity(token);
        PasswordResetTokenJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<PasswordResetToken> findByTokenHash(String tokenHash) {
        return jpaRepository.findByTokenHash(tokenHash).map(this::toDomain);
    }

    @Override
    public void markUsed(UUID tokenId) {
        jpaRepository.updateUsedAt(tokenId, OffsetDateTime.now());
    }

    private PasswordResetToken toDomain(PasswordResetTokenJpaEntity entity) {
        return new PasswordResetToken(entity.getId(), entity.getUserId(), entity.getTokenHash(), entity.getExpiresAt(), entity.getUsedAt(), entity.getCreatedAt());
    }

    private PasswordResetTokenJpaEntity toEntity(PasswordResetToken domain) {
        return PasswordResetTokenJpaEntity.builder()
        .id(domain.id())
        .userId(domain.userId())
        .tokenHash(domain.tokenHash())
        .expiresAt(domain.expiresAt())
        .usedAt(domain.usedAt())
        .createdAt(domain.createdAt())
        .build();
    }
}
