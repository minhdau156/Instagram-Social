package com.instagram.domain.port.in.search;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.Post;

public interface GetPostsByHashtagUseCase {
    List<Post> getPostsByHashtag(Query query);

    record Query(String hashtagName, UUID currentUserId, int page, int size) {
    }
}
