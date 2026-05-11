package com.instagram.domain.port.in.messaging;

import java.util.UUID;

public interface LeaveConversationUseCase {
    void leaveConversation(Command command);

    record Command(UUID conversationId, UUID userId) {
    }
}
