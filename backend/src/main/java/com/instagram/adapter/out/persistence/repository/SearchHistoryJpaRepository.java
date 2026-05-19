package com.instagram.adapter.out.persistence.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.adapter.out.persistence.entity.SearchHistoryJpaEntity;

public interface SearchHistoryJpaRepository extends JpaRepository<SearchHistoryJpaEntity, UUID> {
    Page<SearchHistoryJpaEntity> findByUserIdOrderBySearchedAtDesc(UUID userId, Pageable pageable);

    @Modifying
    @Transactional
    @Query("DELETE FROM SearchHistoryJpaEntity s WHERE s.userId = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

}
