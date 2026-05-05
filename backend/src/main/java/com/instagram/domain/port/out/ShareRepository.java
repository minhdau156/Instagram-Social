package com.instagram.domain.port.out;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.PostShare;

public interface ShareRepository {
    /**
     * Persists a new share record.
     */
    PostShare save(PostShare share);

    /**
     * Returns all share records for a post (analytics purpose).
     */
    List<PostShare> findByPostId(UUID postId);
}
