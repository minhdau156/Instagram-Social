package com.instagram.domain.port.in.messaging;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.Message;

public interface GetMessagesUseCase {
    List<Message> getMessages(Query query);

    record Query(UUID conversationId, UUID requesterId, UUID cursor, int limit) {
    }
}
