package com.instagram.domain.exception;

import java.util.UUID;

public class NotBlockedException extends RuntimeException {

    public NotBlockedException(UUID blockerId, UUID blockedId) {
        super("User " + blockerId + " has not blocked user " + blockedId);
    }
}
