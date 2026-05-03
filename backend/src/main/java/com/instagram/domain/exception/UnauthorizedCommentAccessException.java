package com.instagram.domain.exception;

import java.util.UUID;

public class UnauthorizedCommentAccessException extends RuntimeException {
    public UnauthorizedCommentAccessException(UUID commentId, UUID userId) {
        super(String.format("User '%s' is not authorized to modify comment '%s'", userId, commentId));
    }
}
