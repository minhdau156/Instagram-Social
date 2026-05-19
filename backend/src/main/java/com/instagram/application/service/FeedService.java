package com.instagram.application.service;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostMedia;
import com.instagram.domain.port.in.feed.GetExploreFeedUseCase;
import com.instagram.domain.port.in.feed.GetHomeFeedUseCase;
import com.instagram.domain.port.out.FeedRepository;
import com.instagram.domain.port.out.LikeRepository;
import com.instagram.domain.port.out.PostMediaRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FeedService
                implements GetHomeFeedUseCase,
                GetExploreFeedUseCase {

        private final FeedRepository feedRepository;
        private final PostMediaRepository postMediaRepository;

        private final LikeRepository likeRepository;

        @Override
        public GetHomeFeedUseCase.FeedPage getHomeFeed(GetHomeFeedUseCase.Query query) {
                // TODO: add Redis cache for cursor=null (page 1) after Phase 10
                List<Post> posts = feedRepository.getHomeFeed(
                                query.userId(), query.cursor(), query.limit());
                List<UUID> postIds = posts.stream().map(Post::getId).toList();
                List<PostMedia> postMedias = postMediaRepository.findByPostIds(postIds);
                Map<UUID, List<PostMedia>> postMediasMap = new HashMap<>();
                for (PostMedia postMedia : postMedias) {
                        postMediasMap.putIfAbsent(postMedia.getPostId(), new ArrayList<>());
                        postMediasMap.get(postMedia.getPostId()).add(postMedia);
                }
                for (Post post : posts) {
                        boolean hasLiked = likeRepository.hasLikedPost(post.getId(), query.userId());
                        post.setLikedByCurrentUser(hasLiked);
                }

                UUID nextCursor = posts.size() < query.limit()
                                ? null
                                : posts.get(posts.size() - 1).getId();

                return new GetHomeFeedUseCase.FeedPage(posts, nextCursor, postMediasMap);
        }

        @Override
        public GetExploreFeedUseCase.FeedPage getExploreFeed(GetExploreFeedUseCase.Query query) {
                List<Post> posts = feedRepository.getExploreFeed(
                                query.userId(), query.cursor(), query.limit());
                List<UUID> postIds = posts.stream().map(Post::getId).toList();
                List<PostMedia> postMedias = postMediaRepository.findByPostIds(postIds);
                Map<UUID, List<PostMedia>> postMediasMap = new HashMap<>();
                for (PostMedia postMedia : postMedias) {
                        postMediasMap.putIfAbsent(postMedia.getPostId(), new ArrayList<>());
                        postMediasMap.get(postMedia.getPostId()).add(postMedia);
                }

                UUID nextCursor = posts.size() < query.limit()
                                ? null
                                : posts.get(posts.size() - 1).getId();

                return new GetExploreFeedUseCase.FeedPage(posts, nextCursor, postMediasMap);
        }
}