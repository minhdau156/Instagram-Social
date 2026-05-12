package com.instagram.adapter.in.web.dto.request;

import java.util.UUID;

import com.instagram.domain.model.Message;

public record SendMessageRequest(UUID conversationId, String content, Message.MessageType messageType, String mediaUrl,
        UUID sharedPostId) {

}
