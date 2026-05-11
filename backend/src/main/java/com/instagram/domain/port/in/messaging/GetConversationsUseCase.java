package com.instagram.domain.port.in.messaging;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.Conversation;

public interface GetConversationsUseCase {
    List<Conversation> getConversations(Query query);

    record Query(UUID userId, int page, int size) {
    }
}
