package com.instagram.domain.port.in.search;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.Post;

public interface SearchPostsUseCase {
    List<Post> searchPosts(Query query);

    record Query(String q, UUID currentUserId, int page, int size) {
    }
}
