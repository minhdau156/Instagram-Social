package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.instagram.adapter.out.persistence.entity.PostLikeJpaEntity;
import com.instagram.adapter.out.persistence.entity.PostLikeId;

public interface PostLikeJpaRepository extends JpaRepository<PostLikeJpaEntity, PostLikeId> {

    boolean existsByIdPostIdAndIdUserId(UUID postId, UUID userId);

    void deleteByIdPostIdAndIdUserId(UUID postId, UUID userId);

    Page<PostLikeJpaEntity> findByIdPostIdOrderByCreatedAtDesc(UUID postId, Pageable pageable);
}
