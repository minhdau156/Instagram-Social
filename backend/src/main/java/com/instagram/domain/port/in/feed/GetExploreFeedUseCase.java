package com.instagram.domain.port.in.feed;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostMedia;

public interface GetExploreFeedUseCase {

    FeedPage getExploreFeed(Query query);

    record Query(UUID userId, UUID cursor, int limit) {
        public Query {
            Objects.requireNonNull(userId, "userId must not be null");
            limit = Math.min(Math.max(limit, 1), 50);
        }
    }

    record FeedPage(List<Post> posts, UUID nextCursor, Map<UUID, List<PostMedia>> postMediasMap) {
    }
}
