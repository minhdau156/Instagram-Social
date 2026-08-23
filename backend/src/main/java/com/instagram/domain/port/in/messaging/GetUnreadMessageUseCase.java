package com.instagram.domain.port.in.messaging;

import java.util.UUID;

public interface GetUnreadMessageUseCase {
    int getUnreadMessage(UUID conversationId, UUID userId);
}