package com.instagram.domain.port.in.comment;

import java.util.UUID;

import com.instagram.domain.model.Comment;

public interface AddCommentUseCase {
    Comment addComment(Command command);

    record Command(
            UUID postId,
            UUID userId,
            String content,
            UUID parentId // nullable — null = top-level comment
    ) {
    }
}
