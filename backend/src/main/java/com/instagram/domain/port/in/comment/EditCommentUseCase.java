package com.instagram.domain.port.in.comment;

import java.util.UUID;

import com.instagram.domain.model.Comment;

public interface EditCommentUseCase {
    Comment editComment(Command command);

    record Command(UUID commentId, UUID userId, String newContent) {
    }
}
