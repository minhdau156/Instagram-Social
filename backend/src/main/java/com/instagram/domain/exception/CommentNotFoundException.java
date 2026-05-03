package com.instagram.domain.exception;

import java.util.UUID;

public class CommentNotFoundException extends RuntimeException {
    public CommentNotFoundException(UUID commentId) {
        super(String.format("Comment '%s' not found", commentId));
    }
}
