package com.instagram.domain.port.in.messaging;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.Conversation;

public interface CreateConversationUseCase {

    Conversation createConversation(Command command);

    record Command(UUID creatorId, List<UUID> participantIds, String name, boolean isGroup) {
    }
}
