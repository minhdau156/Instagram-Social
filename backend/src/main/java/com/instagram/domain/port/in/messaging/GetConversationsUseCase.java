package com.instagram.domain.port.in.messaging;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.Conversation;
import com.instagram.domain.model.Message;

public interface GetConversationsUseCase {
    List<ConversationView> getConversations(Query query);

    record Query(UUID userId, int page, int size) {
    }

    record MessageView(Message message, String senderUsername, String senderAvatarUrl) {
    }

    record ConversationView(Conversation conversation, int unreadCount, MessageView lastMessage, String eachOtherName) {
    }
}
