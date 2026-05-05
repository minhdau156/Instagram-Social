package com.instagram.domain.port.in.comment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import com.instagram.domain.model.Comment;

public interface GetRepliesUseCase {
    Page<Comment> getReplies(Query query);

    record Query(UUID commentId, UUID currentUserId, int page, int size) {
    }
}
