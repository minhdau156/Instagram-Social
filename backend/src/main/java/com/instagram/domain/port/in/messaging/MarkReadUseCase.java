package com.instagram.domain.port.in.messaging;

import java.util.UUID;

public interface MarkReadUseCase {
    void markRead(Command command);

    record Command(UUID conversationId, UUID userId) {
    }
}
