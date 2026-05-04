package com.instagram.domain.exception;

import java.util.UUID;

public class NotSavedException extends RuntimeException {
    public NotSavedException(UUID postId, UUID userId) {
        super(String.format("Post '%s' is not saved by user '%s'", postId, userId));
    }
}
