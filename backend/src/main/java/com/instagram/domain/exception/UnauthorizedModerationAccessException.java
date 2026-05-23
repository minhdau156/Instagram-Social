package com.instagram.domain.exception;

import java.util.UUID;

public class UnauthorizedModerationAccessException extends RuntimeException {

    public UnauthorizedModerationAccessException(UUID userId, String action) {
        super("User " + userId + " is not authorized to perform: " + action);
    }
}
