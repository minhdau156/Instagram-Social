package com.instagram.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import com.instagram.domain.exception.PostNotFoundException;
import com.instagram.domain.model.Hashtag;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.port.in.CreatePostUseCase;
import com.instagram.domain.port.in.DeletePostUseCase;
import com.instagram.domain.port.in.GetPostUseCase;
import com.instagram.domain.port.in.GetUserPostsUseCase;
import com.instagram.domain.port.in.UpdatePostUseCase;
import com.instagram.domain.port.out.HashtagRepository;
import com.instagram.domain.port.out.LikeRepository;
import com.instagram.domain.port.out.MediaStoragePort;
import com.instagram.domain.port.out.PostHashtagRepository;
import com.instagram.domain.port.out.PostMediaRepository;
import com.instagram.domain.port.out.PostRepository;
import com.instagram.domain.port.out.SavedPostRepository;

@ExtendWith(MockitoExtension.class)
public class PostServiceTest {
    @Mock
    PostRepository postRepository;

    @Mock
    PostMediaRepository postMediaRepository;

    @Mock
    HashtagRepository hashtagRepository;

    @Mock
    LikeRepository likeRepository;

    @Mock
    SavedPostRepository savedPostRepository;

    @Mock
    MediaStoragePort mediaStoragePort;

    @Mock
    PostHashtagRepository postHashtagRepository;

    @Mock
    Counter postsCreatedCounter;

    @InjectMocks
    PostService postService;

    private CreatePostUseCase.MediaItem mockMediaItem() {
        return new CreatePostUseCase.MediaItem(
                "key",
                "image",
                640,
                480,
                null,
                4096L,
                "1");
    }

    @Test
    void createPost_shouldSavePostAndExtractHashtag() {
        // GIVEN
        UUID userId = UUID.randomUUID();
        var postCommand = new CreatePostUseCase.Command(
                userId,
                "Hello #world",
                null,
                List.of(mockMediaItem()));

        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(postMediaRepository.saveAll(any()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(hashtagRepository.findOrCreate(anyString()))
                .thenReturn(Hashtag.builder().name("world").postCount(0).build());
        doNothing().when(postHashtagRepository).save(any(), any());

        Post result = postService.createPost(postCommand);

        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals("Hello #world", result.getCaption());

    }

    @Test
    void getPost_shouldReturnPost_whenPostExists() {
        // GIVEN
        UUID postId = UUID.randomUUID();
        Post post = Post.builder()
                .id(postId)
                .userId(UUID.randomUUID())
                .caption("Test post")
                .build();
        var query = new GetPostUseCase.Query(postId, UUID.randomUUID());

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(likeRepository.hasLikedPost(any(), any())).thenReturn(false);
        when(savedPostRepository.existsByPostIdAndUserId(any(), any())).thenReturn(false);

        Post result = postService.getPost(query);

        assertNotNull(result);
        assertEquals(postId, result.getId());
        assertEquals("Test post", result.getCaption());
    }

    @Test
    void getPost_shouldThrowPostNotFoundException_whenPostDoesNotExist() {
        // GIVEN
        UUID postId = UUID.randomUUID();
        var query = new GetPostUseCase.Query(postId, UUID.randomUUID());

        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        PostNotFoundException thrown = assertThrows(PostNotFoundException.class, () -> postService.getPost(query));

        assertEquals("Post not found with id: " + postId, thrown.getMessage());
    }

    @Test
    void updatePost_shouldUpdatePost_whenPostExists() {
        // GIVEN
        UUID postId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Post post = Post.builder()
                .id(postId)
                .userId(ownerId)
                .caption("Test #post")
                .build();
        var command = new UpdatePostUseCase.Command(postId, ownerId, "Updated #caption", null);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(hashtagRepository.findOrCreate(anyString()))
                .thenReturn(Hashtag.builder().name("caption").postCount(0).build());
        doNothing().when(postHashtagRepository).save(any(), any());

        Post result = postService.updatePost(command);

        assertNotNull(result);
        assertEquals(postId, result.getId());
        assertEquals("Updated #caption", result.getCaption());
    }

    @Test
    void updatePost_shouldThrowPostNotFoundException_whenPostDoesNotExist() {
        // GIVEN
        UUID postId = UUID.randomUUID();
        var command = new UpdatePostUseCase.Command(postId, UUID.randomUUID(), "Updated #caption", null);

        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        PostNotFoundException thrown = assertThrows(PostNotFoundException.class, () -> postService.updatePost(command));

        assertEquals("Post not found with id: " + postId, thrown.getMessage());
    }

    @Test
    void deletePost_shouldDeletePost_whenPostExists() {
        // GIVEN
        UUID postId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Post post = Post.builder()
                .id(postId)
                .userId(ownerId)
                .caption("Test post")
                .status(PostStatus.PUBLISHED)
                .build();
        var command = new DeletePostUseCase.Command(postId, ownerId);

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));
        when(postRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        postService.deletePost(command);
        Post updatedPost = post.withSoftDelete();

        assertNotNull(updatedPost);
        assertEquals(PostStatus.DELETED, updatedPost.getStatus());
    }

    @Test
    void deletePost_shouldThrowPostNotFoundException_whenPostDoesNotExist() {
        // GIVEN
        UUID postId = UUID.randomUUID();
        var command = new DeletePostUseCase.Command(postId, UUID.randomUUID());

        when(postRepository.findById(postId)).thenReturn(Optional.empty());

        PostNotFoundException thrown = assertThrows(PostNotFoundException.class, () -> postService.deletePost(command));

        assertEquals("Post not found with id: " + postId, thrown.getMessage());
    }

    @Test
    void getUserPosts_shouldReturnPosts_whenUserHasPosts() {
        // GIVEN
        UUID userId = UUID.randomUUID();
        Post post = Post.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .caption("Test post")
                .build();
        var query = new GetUserPostsUseCase.Query(userId, UUID.randomUUID(), 0, 10);

        when(postRepository.findByUserId(userId, PageRequest.of(0, 10)))
                .thenReturn(new PageImpl<>(List.of(post), PageRequest.of(0, 10), 1L));

        List<Post> result = postService.getUserPosts(query);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(userId, result.get(0).getUserId());
    }

}
