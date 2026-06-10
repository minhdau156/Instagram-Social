package com.instagram.adapter.out.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.instagram.adapter.out.persistence.entity.UploadSessionJpaEntity;

public interface UploadSessionJpaRepository extends JpaRepository<UploadSessionJpaEntity, UUID> {

    Optional<UploadSessionJpaEntity> findByUploadId(String uploadId);

    boolean existsByUploadIdAndUserId(String uploadId, UUID userId);
}
