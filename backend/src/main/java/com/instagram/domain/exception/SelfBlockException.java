package com.instagram.domain.exception;

import java.util.UUID;

public class SelfBlockException extends RuntimeException {

    public SelfBlockException(UUID userId) {
        super("User " + userId + " cannot block themselves");
    }
}
