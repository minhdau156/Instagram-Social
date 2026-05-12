package com.instagram.adapter.in.messaging;

import java.util.UUID;

public record TypingEvent(UUID conversationId, UUID userId, boolean isTyping) {
}
