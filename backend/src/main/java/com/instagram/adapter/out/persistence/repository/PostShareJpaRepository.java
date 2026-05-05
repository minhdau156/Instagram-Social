package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.instagram.adapter.out.persistence.entity.PostShareJpaEntity;

public interface PostShareJpaRepository extends JpaRepository<PostShareJpaEntity, UUID> {
    List<PostShareJpaEntity> findByPostId(UUID postId);
}
