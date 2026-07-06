package com.instagram.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import com.instagram.domain.model.PostMedia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.instagram.domain.model.Post;
import com.instagram.domain.port.in.feed.GetExploreFeedUseCase;
import com.instagram.domain.port.in.feed.GetHomeFeedUseCase;
import com.instagram.domain.port.out.FeedRepository;
import com.instagram.domain.port.out.LikeRepository;
import com.instagram.domain.port.out.PostMediaRepository;

@ExtendWith(MockitoExtension.class)
class FeedServiceTest {

    @Mock
    private FeedRepository feedRepository;

    @Mock
    private PostMediaRepository postMediaRepository;

    @Mock
    private LikeRepository likeRepository;

    @InjectMocks
    private FeedService feedService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        when(postMediaRepository.findByPostIds(anyCollection())).thenReturn(List.of());
    }

    @Test
    void getHomeFeed_returnsPostsFromRepository() {
        Post post1 = buildPost();
        Post post2 = buildPost();

        PostMedia postMedia = buildPostMedia();
        PostMedia postMedia2 = buildPostMedia();
        when(feedRepository.getHomeFeed(userId, null, 20)).thenReturn(List.of(post1, post2));
        when(postMediaRepository.findByPostIds(anyCollection())).thenReturn(List.of(postMedia, postMedia2));
        GetHomeFeedUseCase.FeedPage result = feedService.getHomeFeed(
                new GetHomeFeedUseCase.Query(userId, null, 20));

        assertThat(result.posts()).hasSize(2);
        assertThat(result.nextCursor()).isNull();
    }

    @Test
    void getHomeFeed_setsNextCursor_whenFullPageReturned() {
        List<Post> posts = generatePosts(20);
        when(feedRepository.getHomeFeed(userId, null, 20)).thenReturn(posts);

        GetHomeFeedUseCase.FeedPage result = feedService.getHomeFeed(
                new GetHomeFeedUseCase.Query(userId, null, 20));

        assertThat(result.nextCursor()).isEqualTo(posts.get(19).getId());
    }

    @Test
    void getExploreFeed_delegatesToRepository() {
        Post post = buildPost();
        PostMedia postMedia = buildPostMedia();
        PostMedia postMedia2 = buildPostMedia();
        when(feedRepository.getExploreFeed(userId, null, 20)).thenReturn(List.of(post));
        when(postMediaRepository.findByPostIds(anyCollection())).thenReturn(List.of(postMedia, postMedia2));
        GetExploreFeedUseCase.FeedPage result = feedService.getExploreFeed(
                new GetExploreFeedUseCase.Query(userId, null, 20));

        assertThat(result.posts()).hasSize(1);
        assertThat(result.nextCursor()).isNull();
    }

    private Post buildPost() {
        return Post.builder().id(UUID.randomUUID()).userId(userId).build();
    }

    private List<Post> generatePosts(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> buildPost())
                .toList();
    }

    private PostMedia buildPostMedia() {
        return PostMedia.builder()
                .id(UUID.randomUUID())
                .mediaUrl("image.jpg")
                .build();
    }
}
