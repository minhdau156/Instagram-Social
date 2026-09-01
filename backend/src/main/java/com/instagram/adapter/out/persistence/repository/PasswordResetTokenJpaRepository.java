package com.instagram.adapter.out.persistence.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.instagram.adapter.out.persistence.entity.PasswordResetTokenJpaEntity;

public interface PasswordResetTokenJpaRepository extends JpaRepository<PasswordResetTokenJpaEntity, UUID> {
    
    Optional<PasswordResetTokenJpaEntity> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE PasswordResetTokenJpaEntity p SET p.usedAt = :usedAt WHERE p.id = :id AND p.usedAt IS NULL")
    void updateUsedAt(@Param("id") UUID id, @Param("usedAt") OffsetDateTime usedAt);
}
