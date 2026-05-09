package com.instagram.adapter.out.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.instagram.adapter.out.persistence.entity.HashtagJpaEntity;

public interface HashtagJpaRepository extends JpaRepository<HashtagJpaEntity, UUID> {
    Optional<HashtagJpaEntity> findByName(String name);

    // Top trending hashtags for the Explore page
    Page<HashtagJpaEntity> findTopByOrderByPostCountDesc(Pageable pageable);

}
