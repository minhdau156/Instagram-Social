package com.instagram.application.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.instagram.domain.model.Post;
import com.instagram.domain.port.in.feed.GetExploreFeedUseCase;
import com.instagram.domain.port.in.feed.GetHomeFeedUseCase;
import com.instagram.domain.port.out.FeedRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeedService
        implements GetHomeFeedUseCase,
        GetExploreFeedUseCase {

    private final FeedRepository feedRepository;

    @Override
    public GetHomeFeedUseCase.FeedPage getHomeFeed(GetHomeFeedUseCase.Query query) {
        // TODO: add Redis cache for cursor=null (page 1) after Phase 10
        List<Post> posts = feedRepository.getHomeFeed(
                query.userId(), query.cursor(), query.limit());

        UUID nextCursor = posts.size() < query.limit()
                ? null
                : posts.get(posts.size() - 1).getId();

        return new GetHomeFeedUseCase.FeedPage(posts, nextCursor);
    }

    @Override
    public GetExploreFeedUseCase.FeedPage getExploreFeed(GetExploreFeedUseCase.Query query) {
        List<Post> posts = feedRepository.getExploreFeed(
                query.userId(), query.cursor(), query.limit());

        UUID nextCursor = posts.size() < query.limit()
                ? null
                : posts.get(posts.size() - 1).getId();

        return new GetExploreFeedUseCase.FeedPage(posts, nextCursor);
    }
}