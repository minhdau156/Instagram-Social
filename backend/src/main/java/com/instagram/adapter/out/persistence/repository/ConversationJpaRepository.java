package com.instagram.adapter.out.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.instagram.adapter.out.persistence.entity.ConversationJpaEntity;

public interface ConversationJpaRepository extends JpaRepository<ConversationJpaEntity, UUID> {

    @Query("SELECT c FROM ConversationJpaEntity c " +
            "JOIN ConversationMemberJpaEntity m ON m.id.conversationId = c.id " +
            "WHERE m.id.userId = :userId " +
            "ORDER BY c.updatedAt DESC")
    Page<ConversationJpaEntity> findByMemberId(@Param("userId") UUID userId, Pageable pageable);

    @Query("SELECT c FROM ConversationJpaEntity c " +
            "WHERE c.isGroup = false " +
            "AND EXISTS (SELECT m FROM ConversationMemberJpaEntity m WHERE m.id.conversationId = c.id AND m.id.userId = :userId1) "
            +
            "AND EXISTS (SELECT m FROM ConversationMemberJpaEntity m WHERE m.id.conversationId = c.id AND m.id.userId = :userId2)")
    Optional<ConversationJpaEntity> findExisting1to1(@Param("userId1") UUID userId1, @Param("userId2") UUID userId2);

}
