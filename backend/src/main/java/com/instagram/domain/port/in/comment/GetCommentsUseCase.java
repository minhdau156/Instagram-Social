package com.instagram.domain.port.in.comment;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import com.instagram.domain.model.Comment;

public interface GetCommentsUseCase {
    List<Comment> getComments(Query query);

    record Query(UUID postId, UUID currentUserId, String cursor, int size) {
    }
}
