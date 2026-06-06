package com.instagram.domain.port.out;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.instagram.domain.model.Message;

public interface MessageRepository {
    Message save(Message message);

    Optional<Message> findById(UUID messageId);

    List<Message> findByConversationId(UUID conversationId, UUID cursor, int limit);

    void markAsRead(UUID conversationId, UUID messageId, UUID userId, OffsetDateTime readAt);

    int getUnreadCount(UUID conversationId, UUID userId);

    Optional<Message> findLatestByConversationId(UUID conversationId);

    List<Message> findLatestByConversationIds(List<UUID> conversationIds);

    Map<UUID, Long> getUnreadCountsByConversationIds(List<UUID> conversationIds, UUID userId);
}
