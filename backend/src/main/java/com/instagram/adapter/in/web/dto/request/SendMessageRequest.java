package com.instagram.adapter.in.web.dto.request;

import java.util.UUID;

import com.instagram.domain.model.Message;

import jakarta.validation.constraints.NotNull;

public record SendMessageRequest(
        String content,
        @NotNull Message.MessageType messageType,
        String mediaUrl,
        UUID sharedPostId) {
}
