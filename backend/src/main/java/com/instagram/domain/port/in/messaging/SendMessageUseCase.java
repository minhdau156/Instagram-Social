package com.instagram.domain.port.in.messaging;

import java.util.UUID;

import com.instagram.domain.model.Message;

public interface SendMessageUseCase {
    Message sendMessage(Command command);

    record Command(UUID conversationId, UUID senderId, String content, Message.MessageType messageType,
            String mediaUrl, UUID sharedPostId) {
    }
}
