package com.instagram.domain.exception;

import java.util.UUID;

public class AlreadySavedException extends RuntimeException {
    public AlreadySavedException(UUID postId, UUID userId) {
        super(String.format("Post '%s' is already saved by user '%s'", postId, userId));
    }
}
