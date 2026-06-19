package com.instagram.domain.exception;

public class InvalidMediaException extends RuntimeException {
    public InvalidMediaException(String message) {
        super(message);
    }
}
