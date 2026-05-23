package com.instagram.adapter.out.persistence.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.instagram.adapter.out.persistence.entity.AuditLogJpaEntity;

public interface AuditLogJpaRepository extends JpaRepository<AuditLogJpaEntity, Long> {

    Page<AuditLogJpaEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<AuditLogJpaEntity> findByActionOrderByCreatedAtDesc(String action, Pageable pageable);

}
