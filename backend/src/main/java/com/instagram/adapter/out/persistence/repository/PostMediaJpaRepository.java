package com.instagram.adapter.out.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.instagram.adapter.out.persistence.entity.PostMediaJpaEntity;

public interface PostMediaJpaRepository extends JpaRepository<PostMediaJpaEntity, UUID> {
    List<PostMediaJpaEntity> findByPostIdOrderBySortOrderAsc(UUID postId);

    List<PostMediaJpaEntity> findByPostIdIn(Collection<UUID> postIds);
}
