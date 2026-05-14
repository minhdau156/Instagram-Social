package com.instagram.adapter.in.web.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.instagram.domain.model.Conversation;

public record ConversationResponse(
        UUID id, String name, boolean isGroup, MessageResponse lastMessage,
        int unreadCount, String eachOtherName, OffsetDateTime createdAt) {

    public static ConversationResponse from(Conversation conversation, MessageResponse lastMessage,
            int unreadCount, String eachOtherName) {
        return new ConversationResponse(conversation.getId(), conversation.getName(), conversation.isGroup(),
                lastMessage, unreadCount, eachOtherName, conversation.getCreatedAt());
    }
}
