package com.instagram.adapter.out.persistence.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.instagram.adapter.out.persistence.entity.SavePostId;
import com.instagram.adapter.out.persistence.entity.SavedPostJpaEntity;

public interface SavedPostJpaRepository extends JpaRepository<SavedPostJpaEntity, SavePostId> {

    boolean existsByIdPostIdAndIdUserId(UUID postId, UUID userId);

    void deleteByIdPostIdAndIdUserId(UUID postId, UUID userId);

    Page<SavedPostJpaEntity> findByIdUserIdOrderBySavedAtDesc(UUID userId, Pageable pageable);
}
