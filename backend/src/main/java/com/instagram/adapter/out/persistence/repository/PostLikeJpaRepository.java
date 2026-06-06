package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.instagram.adapter.out.persistence.entity.PostLikeJpaEntity;
import com.instagram.adapter.out.persistence.entity.PostLikeId;

public interface PostLikeJpaRepository extends JpaRepository<PostLikeJpaEntity, PostLikeId> {

    boolean existsByIdPostIdAndIdUserId(UUID postId, UUID userId);

    void deleteByIdPostIdAndIdUserId(UUID postId, UUID userId);

    Page<PostLikeJpaEntity> findByIdPostIdOrderByCreatedAtDesc(UUID postId, Pageable pageable);

    @Query(value = "SELECT post_id FROM post_like " +
            "WHERE user_id = :userId AND post_id IN :postIds", nativeQuery = true)
    Set<UUID> findByUserIdAndPostIdIn(@Param("userId") UUID userId, @Param("postIds") List<UUID> postIds);
}
