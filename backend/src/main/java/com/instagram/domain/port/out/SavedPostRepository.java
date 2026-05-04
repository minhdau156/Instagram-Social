package com.instagram.domain.port.out;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.instagram.domain.model.SavedPost;

public interface SavedPostRepository {

    /**
     * Persists a new saved post record.
     */
    SavedPost save(SavedPost savedPost);

    /**
     * Removes the saved post record for a given user and post.
     * No-op if record does not exist.
     */
    void delete(UUID postId, UUID userId);

    /**
     * Returns true if the given user has already saved the given post.
     */
    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    /**
     * Returns paginated saved posts for a user, ordered by savedAt descending.
     */
    Page<SavedPost> findByUserId(UUID userId, Pageable pageable);
}
