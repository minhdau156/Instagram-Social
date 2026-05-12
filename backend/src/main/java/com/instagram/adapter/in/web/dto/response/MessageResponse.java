package com.instagram.adapter.in.web.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.instagram.domain.model.Message;

public record MessageResponse(
        UUID id, UUID conversationId, UUID senderId, String senderUsername,
        String senderAvatarUrl, String content, Message.MessageType messageType, String mediaUrl,
        UUID sharedPostId, Message.MessageStatus status, OffsetDateTime createdAt) {

    public static MessageResponse from(Message message, String senderUsername,
            String senderAvatarUrl) {
        return new MessageResponse(message.getId(), message.getConversationId(), message.getSenderId(),
                senderUsername, senderAvatarUrl, message.getContent(),
                message.getMessageType(), message.getMediaUrl(), message.getSharedPostId(),
                message.getStatus(), message.getCreatedAt());
    }
}
