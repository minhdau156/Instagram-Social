package com.instagram.domain.exception;

import java.util.UUID;

public class NotConversationMemberException extends RuntimeException {

    public NotConversationMemberException(UUID userId, UUID conversationId) {
        super("User " + userId + " is not a member of conversation " + conversationId);
    }
}
