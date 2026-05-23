package com.instagram.domain.exception;

import java.util.UUID;

public class AlreadyBlockedException extends RuntimeException {

    public AlreadyBlockedException(UUID blockerId, UUID blockedId) {
        super("User " + blockerId + " has already blocked user " + blockedId);
    }
}
