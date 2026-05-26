package com.instagram.domain.exception;

public class InsufficientPrivilegeException extends RuntimeException {

    public InsufficientPrivilegeException(String message) {
        super(message);
    }
}
