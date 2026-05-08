package com.instagram.domain.port.out;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.Hashtag;
import com.instagram.domain.model.Post;

public interface FeedRepository {
    /**
     * Returns posts from users that userId follows, newest first.
     * cursor: UUID of last seen post (null = first page).
     */
    List<Post> getHomeFeed(UUID userId, UUID cursor, int limit);

    /**
     * Returns posts NOT from followed users, ranked by engagement.
     * Excludes posts the user has already interacted with (based on
     * user_interests).
     */
    List<Post> getExploreFeed(UUID userId, UUID cursor, int limit);

    /**
     * Returns trending hashtags ordered by weekly_count DESC.
     */
    List<Hashtag> getTrendingHashtags(int limit);
}
