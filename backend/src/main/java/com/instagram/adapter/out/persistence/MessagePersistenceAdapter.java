package com.instagram.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.MessageJpaEntity;
import com.instagram.adapter.out.persistence.repository.MessageJpaRepository;
import com.instagram.adapter.out.persistence.repository.MessageReadJpaRepository;
import com.instagram.domain.model.Message;
import com.instagram.domain.port.out.MessageRepository;

@Component
public class MessagePersistenceAdapter implements MessageRepository {

    private final MessageJpaRepository messageJpaRepository;
    private final MessageReadJpaRepository messageReadJpaRepository;

    public MessagePersistenceAdapter(MessageJpaRepository messageJpaRepository,
            MessageReadJpaRepository messageReadJpaRepository) {
        this.messageJpaRepository = messageJpaRepository;
        this.messageReadJpaRepository = messageReadJpaRepository;
    }

    @Override
    public Message save(Message message) {
        MessageJpaEntity savedMessage = this.messageJpaRepository.save(toEntity(message));
        return toDomain(savedMessage);
    }

    @Override
    public Optional<Message> findById(UUID messageId) {
        return this.messageJpaRepository.findById(messageId).map(this::toDomain);
    }

    @Override
    public List<Message> findByConversationId(UUID conversationId, UUID cursor, int limit) {
        if (cursor == null) {
            return this.messageJpaRepository
                    .findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, limit)).stream()
                    .map(this::toDomain).toList();
        } else {
            return this.messageJpaRepository.findOlderThanCursor(conversationId, cursor, PageRequest.of(0, limit))
                    .stream().map(this::toDomain).toList();
        }
    }

    @Override
    public void markAsRead(UUID conversationId, UUID messageId, UUID userId, OffsetDateTime readAt) {
        this.messageReadJpaRepository.markConversationReadUpToMessage(conversationId, userId, messageId,
                readAt);
    }

    @Override
    public int getUnreadCount(UUID conversationId, UUID userId) {
        return this.messageReadJpaRepository.countUnread(conversationId, userId);
    }

    @Override
    public Optional<Message> findLatestByConversationId(UUID conversationId) {
        return this.messageJpaRepository.findTopByConversationIdOrderByCreatedAtDesc(conversationId)
                .map(this::toDomain);
    }

    private MessageJpaEntity toEntity(Message message) {
        return MessageJpaEntity.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .messageType(message.getMessageType())
                .mediaUrl(message.getMediaUrl())
                .sharedPostId(message.getSharedPostId())
                .createdAt(message.getCreatedAt())
                .replyToMessageId(message.getReplyToMessageId())
                .updatedAt(message.getUpdatedAt())
                .build();
    }

    private Message toDomain(MessageJpaEntity messageJpaEntity) {
        return Message.builder()
                .id(messageJpaEntity.getId())
                .conversationId(messageJpaEntity.getConversationId())
                .senderId(messageJpaEntity.getSenderId())
                .content(messageJpaEntity.getContent())
                .messageType(messageJpaEntity.getMessageType())
                .mediaUrl(messageJpaEntity.getMediaUrl())
                .sharedPostId(messageJpaEntity.getSharedPostId())
                .status(Message.MessageStatus.SENT)
                .createdAt(messageJpaEntity.getCreatedAt())
                .replyToMessageId(messageJpaEntity.getReplyToMessageId())
                .updatedAt(messageJpaEntity.getUpdatedAt())
                .build();
    }

}
