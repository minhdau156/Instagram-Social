package com.instagram.adapter.in.messaging;

import java.util.UUID;

public record TypingPayload(UUID conversationId, boolean isTyping) {
}
