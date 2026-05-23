package com.instagram.adapter.out.persistence.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.instagram.adapter.out.persistence.entity.ReportJpaEntity;
import com.instagram.domain.model.ReportStatus;

public interface ReportJpaRepository extends JpaRepository<ReportJpaEntity, UUID> {
    Page<ReportJpaEntity> findByStatusOrderByCreatedAtAsc(ReportStatus status, Pageable pageable);

    Page<ReportJpaEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<ReportJpaEntity> findByStatusOrderByCreatedAtDesc(ReportStatus status, Pageable pageable);

    long countByStatus(ReportStatus status);

    boolean existsByReporterIdAndEntityId(UUID reporterId, UUID entityId);
}
