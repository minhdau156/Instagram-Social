package com.instagram.adapter.out.persistence.repository;

import com.instagram.adapter.out.persistence.entity.IdempotencyKeyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyJpaEntity, UUID> {

    Optional<IdempotencyKeyJpaEntity> findByKeyAndUserId(UUID key, UUID userId);
}