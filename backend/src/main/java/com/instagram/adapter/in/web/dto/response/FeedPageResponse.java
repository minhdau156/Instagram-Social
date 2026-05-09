package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.port.in.feed.GetExploreFeedUseCase;
import com.instagram.domain.port.in.feed.GetHomeFeedUseCase;

import java.util.List;

public record FeedPageResponse(
        List<PostResponse> posts,
        String nextCursor) {
    public static FeedPageResponse from(GetHomeFeedUseCase.FeedPage page) {
        List<PostResponse> postResponses = page.posts().stream()
                .map(post -> {
                    return PostResponse.from(post, null);
                })
                .toList();
        String cursor = page.nextCursor() != null
                ? page.nextCursor().toString()
                : null;
        return new FeedPageResponse(postResponses, cursor);
    }

    /** Overload for explore feed — same structure, same mapping. */
    public static FeedPageResponse from(GetExploreFeedUseCase.FeedPage page) {
        List<PostResponse> postResponses = page.posts().stream()
                .map(post -> {
                    return PostResponse.from(post, null);
                })
                .toList();
        String cursor = page.nextCursor() != null
                ? page.nextCursor().toString()
                : null;
        return new FeedPageResponse(postResponses, cursor);
    }
}
