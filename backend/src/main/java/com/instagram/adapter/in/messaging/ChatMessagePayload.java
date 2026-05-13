package com.instagram.adapter.in.messaging;

import java.util.UUID;

import com.instagram.domain.model.Message;

public record ChatMessagePayload(
        UUID conversationId,
        String content,
        Message.MessageType messageType,
        String mediaUrl,
        UUID sharedPostId) {
}
