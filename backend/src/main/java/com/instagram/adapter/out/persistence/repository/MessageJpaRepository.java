package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.instagram.adapter.out.persistence.entity.MessageJpaEntity;

public interface MessageJpaRepository extends JpaRepository<MessageJpaEntity, UUID> {
        @Query("SELECT m FROM MessageJpaEntity m WHERE m.conversationId = :convId AND m.createdAt < " +
                        "(SELECT m2.createdAt FROM MessageJpaEntity m2 WHERE m2.id = :cursor) " +
                        "ORDER BY m.createdAt DESC")
        List<MessageJpaEntity> findOlderThanCursor(@Param("convId") UUID conversationId,
                        @Param("cursor") UUID cursorMessageId,
                        Pageable pageable);

        List<MessageJpaEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable);

        Optional<MessageJpaEntity> findTopByConversationIdOrderByCreatedAtDesc(UUID conversationId);

        @Query(value = "SELECT DISTINCT ON (m.conversation_id) * " +
                        "FROM messages m " +
                        "WHERE m.conversation_id IN (:conversationIds) " +
                        "ORDER BY m.conversation_id, m.created_at DESC, m.id DESC", nativeQuery = true)
        List<MessageJpaEntity> findLatestByConversationIdsIn(@Param("conversationIds") List<UUID> conversationIds);

}
