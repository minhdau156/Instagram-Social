package com.instagram.domain.port.in;

import com.instagram.domain.model.Post;

import java.util.List;
import java.util.UUID;

public interface GetUserPostsUseCase {

    List<Post> getUserPosts(Query query);

    record Query(UUID targetUserId, UUID currentUserId, int page, int size) {
    }
}
