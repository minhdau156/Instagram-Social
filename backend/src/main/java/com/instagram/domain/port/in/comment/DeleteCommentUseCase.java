package com.instagram.domain.port.in.comment;

import java.util.UUID;

public interface DeleteCommentUseCase {
    void deleteComment(Command command);

    record Command(UUID commentId, UUID userId) {
    }
}
