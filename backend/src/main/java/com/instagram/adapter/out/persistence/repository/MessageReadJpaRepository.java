package com.instagram.adapter.out.persistence.repository;

import java.time.OffsetDateTime;
import java.util.List;
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
                        "SELECT m.id, :userId, :readAt " +
                        "FROM messages m " +
                        "WHERE m.conversation_id = :convId " +
                        "AND m.sender_id <> :userId " +
                        "AND m.created_at <= (SELECT created_at FROM messages WHERE id = :messageId) " +
                        "ON CONFLICT (message_id, user_id) DO UPDATE SET read_at = EXCLUDED.read_at", nativeQuery = true)
        void markConversationReadUpToMessage(@Param("convId") UUID conversationId,
                        @Param("userId") UUID userId,
                        @Param("messageId") UUID messageId,
                        @Param("readAt") OffsetDateTime readAt);

        @Query(value = "SELECT m.conversation_id, COUNT(*) as unread_count " +
                        "FROM messages m " +
                        "WHERE m.conversation_id IN (:conversationIds) " +
                        "AND m.sender_id <> :userId " +
                        "AND NOT EXISTS (SELECT 1 FROM message_reads r WHERE r.message_id = m.id AND r.user_id = :userId) "
                        +
                        "GROUP BY m.conversation_id", nativeQuery = true)
        List<Object[]> countUnreadMessagesPerConversation(
                        @Param("conversationIds") List<UUID> conversationIds,
                        @Param("userId") UUID userId);

}
