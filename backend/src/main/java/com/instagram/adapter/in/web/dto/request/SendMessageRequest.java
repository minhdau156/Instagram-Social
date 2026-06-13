package com.instagram.adapter.in.web.dto.request;

import java.util.UUID;

import com.instagram.domain.model.Message;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
        @Size(max = 2000) String content,
        @NotNull Message.MessageType messageType,
        String mediaUrl,
        UUID sharedPostId) {
}
