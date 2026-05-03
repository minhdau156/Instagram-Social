package com.instagram.domain.port.in.comment;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.Comment;

public interface GetRepliesUseCase {
    List<Comment> getReplies(Query query);

    record Query(UUID commentId, int page, int size) {
    }
}
