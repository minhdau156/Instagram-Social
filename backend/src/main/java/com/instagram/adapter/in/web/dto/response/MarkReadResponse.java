package com.instagram.adapter.in.web.dto.response;

import java.util.UUID;

public record MarkReadResponse(UUID conversationId, int unreadCount) {
    
}
