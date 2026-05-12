package com.instagram.adapter.out.persistence.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.adapter.out.persistence.entity.MessageReadId;
import com.instagram.adapter.out.persistence.entity.MessageReadJpaEntity;

public interface MessageReadJpaRepository extends JpaRepository<MessageReadJpaEntity, MessageReadId> {
        // @Query("SELECT COUNT(m) FROM MessageJpaEntity m " +
        // "WHERE m.conversationId = :convId " +
        // "AND m.senderId <> :userId " +
        // "AND NOT EXISTS (" +
        // " SELECT 1 FROM MessageReadJpaEntity r " +
        // " WHERE r.id.messageId = m.id " +
        // " AND r.id.userId = :userId" +
        // ")")
        // int countUnread(@Param("convId") UUID conversationId, @Param("userId") UUID
        // userId);

        @Query("SELECT COUNT(m) FROM MessageJpaEntity m " +
                        "WHERE m.conversationId = :convId " +
                        "AND m.senderId <> :userId " +
                        "AND m.createdAt > COALESCE(" +
                        "  (SELECT r.readAt FROM MessageReadJpaEntity r WHERE r.id.messageId = m.id AND r.id.userId = :userId), "
                        +
                        "  CAST('1970-01-01' AS java.time.OffsetDateTime))")
        int countUnread(@Param("convId") UUID conversationId, @Param("userId") UUID userId);

        @Modifying
        @Transactional
        @Query(value = "INSERT INTO message_reads (message_id, user_id, read_at) " +
                        "VALUES (:messageId, :userId, :readAt) " +
                        "ON CONFLICT (message_id, user_id) DO UPDATE SET read_at = EXCLUDED.read_at", nativeQuery = true)
        void upsertReadTimestamp(@Param("messageId") UUID messageId, @Param("userId") UUID userId,
                        @Param("readAt") OffsetDateTime readAt);
}
