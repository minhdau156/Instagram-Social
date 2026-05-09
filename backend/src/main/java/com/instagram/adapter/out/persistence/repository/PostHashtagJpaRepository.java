package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.instagram.adapter.out.persistence.entity.PostHashtagId;
import com.instagram.adapter.out.persistence.entity.PostHashtagJpaEntity;

public interface PostHashtagJpaRepository
        extends JpaRepository<PostHashtagJpaEntity, PostHashtagId> {
    @Query("SELECT ph.id.hashtagId FROM PostHashtagJpaEntity ph WHERE ph.id.postId = :postId")
    List<UUID> findHashtagIdsByPostId(@Param("postId") UUID postId);
}
