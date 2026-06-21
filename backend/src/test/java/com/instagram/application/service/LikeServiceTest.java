package com.instagram.application.service;

import com.instagram.domain.event.NotificationEvent;
import com.instagram.domain.exception.AlreadyLikedException;
import com.instagram.domain.exception.CommentNotFoundException;
import com.instagram.domain.exception.NotLikedException;
import com.instagram.domain.exception.PostNotFoundException;
import com.instagram.domain.model.Comment;
import com.instagram.domain.model.CommentStatus;
import com.instagram.domain.model.FollowStatus;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserSummary;
import com.instagram.domain.port.in.like.GetPostLikersUseCase;
import com.instagram.domain.port.in.like.LikeCommentUseCase;
import com.instagram.domain.port.in.like.LikePostUseCase;
import com.instagram.domain.port.in.like.UnlikeCommentUseCase;
import com.instagram.domain.port.in.like.UnlikePostUseCase;
import com.instagram.domain.port.out.CommentRepository;
import com.instagram.domain.port.out.FollowRepository;
import com.instagram.domain.port.out.LikeRepository;
import com.instagram.domain.port.out.PostRepository;
import com.instagram.domain.port.out.UserInterestPort;
import com.instagram.domain.port.out.UserRepository;
import io.micrometer.core.instrument.Counter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.Map;

@ExtendWith(MockitoExtension.class)
class LikeServiceTest {

    @Mock
    private LikeRepository likeRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserInterestPort userInterestPort;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private Counter likesAddedCounter;

    @InjectMocks
    private LikeService likeService;

    private UUID userId;
    private UUID postId;
    private UUID commentId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        postId = UUID.randomUUID();
        commentId = UUID.randomUUID();
    }

    @Test
    void like_notYetLiked_savesLikeRecord() {
        LikePostUseCase.Command command = new LikePostUseCase.Command(postId, userId);
        Post post = Post.builder()
                .id(postId)
                .userId(UUID.randomUUID())
                .build();

        when(likeRepository.hasLikedPost(postId, userId)).thenReturn(false);
        when(postRepository.findById(any(UUID.class))).thenReturn(Optional.of(post));

        likeService.like(command);

        verify(likeRepository).likePost(postId, userId);
        verify(eventPublisher).publishEvent(any(NotificationEvent.class));
    }

    @Test
    void like_postNotFound_throwsException() {
        LikePostUseCase.Command command = new LikePostUseCase.Command(postId, userId);
        when(likeRepository.hasLikedPost(postId, userId)).thenReturn(false);
        when(postRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(PostNotFoundException.class, () -> likeService.like(command));
    }

    @Test
    void like_alreadyLiked_throwsAlreadyLikedException() {
        LikePostUseCase.Command command = new LikePostUseCase.Command(postId, userId);
        when(likeRepository.hasLikedPost(postId, userId)).thenReturn(true);

        assertThrows(AlreadyLikedException.class, () -> likeService.like(command));

        verify(likeRepository, never()).likePost(any(), any());
    }

    @Test
    void unlike_hasLiked_deletesLikeRecord() {
        UnlikePostUseCase.Command command = new UnlikePostUseCase.Command(postId, userId);
        when(likeRepository.hasLikedPost(postId, userId)).thenReturn(true);

        likeService.unlike(command);

        verify(likeRepository).unlikePost(postId, userId);
    }

    @Test
    void unlike_notLikedYet_throwsNotLikedException() {
        UnlikePostUseCase.Command command = new UnlikePostUseCase.Command(postId, userId);
        when(likeRepository.hasLikedPost(postId, userId)).thenReturn(false);

        assertThrows(NotLikedException.class, () -> likeService.unlike(command));

        verify(likeRepository, never()).unlikePost(any(), any());
    }

    @Test
    void likeComment_notLikedYet_savesCommentLikeRecord() {
        LikeCommentUseCase.Command command = new LikeCommentUseCase.Command(commentId, userId);
        when(likeRepository.hasLikedComment(commentId, userId)).thenReturn(false);
        Comment comment = Comment.builder()
                .id(commentId)
                .userId(UUID.randomUUID())
                .postId(postId)
                .content("Test content")
                .status(CommentStatus.ACTIVE)
                .build();
        when(commentRepository.findById(any(UUID.class))).thenReturn(Optional.of(comment));

        likeService.likeComment(command);

        verify(likeRepository).likeComment(commentId, userId);
        verify(eventPublisher).publishEvent(any(NotificationEvent.class));
    }

    @Test
    void likeComment_commentNotFound_throwsException() {
        LikeCommentUseCase.Command command = new LikeCommentUseCase.Command(commentId, userId);
        when(likeRepository.hasLikedComment(commentId, userId)).thenReturn(false);
        when(commentRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        assertThrows(CommentNotFoundException.class, () -> likeService.likeComment(command));

    }

    @Test
    void likeComment_alreadyLiked_throwsAlreadyLikedException() {
        LikeCommentUseCase.Command command = new LikeCommentUseCase.Command(commentId, userId);
        when(likeRepository.hasLikedComment(commentId, userId)).thenReturn(true);

        assertThrows(AlreadyLikedException.class, () -> likeService.likeComment(command));

        verify(likeRepository, never()).likeComment(any(), any());
    }

    @Test
    void unlikeComment_hasLiked_deletesCommentLikeRecord() {
        UnlikeCommentUseCase.Command command = new UnlikeCommentUseCase.Command(commentId, userId);
        when(likeRepository.hasLikedComment(commentId, userId)).thenReturn(true);

        likeService.unlikeComment(command);

        verify(likeRepository).unlikeComment(commentId, userId);
    }

    @Test
    void unlikeComment_notLikedYet_throwsNotLikedException() {
        UnlikeCommentUseCase.Command command = new UnlikeCommentUseCase.Command(commentId, userId);
        when(likeRepository.hasLikedComment(commentId, userId)).thenReturn(false);

        assertThrows(NotLikedException.class, () -> likeService.unlikeComment(command));

        verify(likeRepository, never()).unlikeComment(any(), any());
    }

    @Test
    void getLikers_Success() {
        UUID requestingUserId = UUID.randomUUID();
        GetPostLikersUseCase.Query query = new GetPostLikersUseCase.Query(postId, requestingUserId, 0, 10);

        List<UUID> likerIds = List.of(userId);
        User user = User.builder()
                .id(userId)
                .username("testuser")
                .email("testuser@test.com")
                .fullName("Test User")
                .passwordHash("test_hash")
                .profilePictureUrl("http://pic")
                .bio("bio")
                .isVerified(true)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .build();

        when(likeRepository.findPostLikerIds(eq(postId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(likerIds, Pageable.unpaged(), 1));
        when(userRepository.findAllByIds(likerIds)).thenReturn(List.of(user));
        when(followRepository.findFollowStatusByFollowerIdAndFollowingIds(eq(requestingUserId), anyList()))
                .thenReturn(Map.of(userId, FollowStatus.ACCEPTED));

        Page<UserSummary> summaries = likeService.getPostLikers(query);

        assertEquals(1, summaries.getContent().size());
        UserSummary summary = summaries.getContent().get(0);
        assertEquals(userId, summary.id());
        assertEquals("testuser", summary.username());
        assertEquals(FollowStatus.ACCEPTED, summary.followStatus());
    }
}
