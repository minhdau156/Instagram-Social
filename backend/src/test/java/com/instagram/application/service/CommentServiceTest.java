package com.instagram.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.instagram.domain.exception.CommentNotFoundException;
import com.instagram.domain.exception.UnauthorizedCommentAccessException;
import com.instagram.domain.model.Comment;
import com.instagram.domain.port.in.comment.AddCommentUseCase;
import com.instagram.domain.port.in.comment.DeleteCommentUseCase;
import com.instagram.domain.port.in.comment.EditCommentUseCase;
import com.instagram.domain.port.out.CommentRepository;
import com.instagram.domain.port.out.LikeRepository;
import com.instagram.domain.port.out.UserRepository;

@ExtendWith(MockitoExtension.class)
public class CommentServiceTest {
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private LikeRepository likeRepository;
    @InjectMocks
    private CommentService commentService;

    UUID postId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    UUID commentId = UUID.randomUUID();
    String content = "This is a comment";
    UUID parentId = UUID.randomUUID();

    @Test
    void addComment_topLevel_savesAndIncrementsPostCommentCount() {
        Comment comment = Comment.of(postId, userId, content, null);
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);
        doNothing().when(commentRepository).incrementPostCommentCount(postId);
        Comment addedComment = commentService.addComment(new AddCommentUseCase.Command(postId, userId, content, null));

        assertEquals(content, addedComment.getContent());
        assertEquals(postId, addedComment.getPostId());
        assertEquals(userId, addedComment.getUserId());
        assertEquals(null, addedComment.getParentId());

        verify(commentRepository).save(any(Comment.class));
        verify(commentRepository).incrementPostCommentCount(postId);
    }

    @Test
    void addComment_reply_savesAndIncrementsReplyCount() {
        Comment comment = Comment.of(postId, userId, content, parentId);
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);
        doNothing().when(commentRepository).incrementReplyCount(parentId);
        doNothing().when(commentRepository).incrementPostCommentCount(postId);
        Comment addedComment = commentService
                .addComment(new AddCommentUseCase.Command(postId, userId, content, parentId));

        assertEquals(content, addedComment.getContent());
        assertEquals(postId, addedComment.getPostId());
        assertEquals(userId, addedComment.getUserId());
        assertEquals(parentId, addedComment.getParentId());

        verify(commentRepository).save(any(Comment.class));
        verify(commentRepository).incrementReplyCount(parentId);
        verify(commentRepository).incrementPostCommentCount(postId);
    }

    @Test
    void editComment_owner_updatesContent() {
        Comment comment = Comment.of(postId, userId, content, null);
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        Comment editedComment = commentService.editComment(new EditCommentUseCase.Command(commentId, userId, content));

        assertEquals(content, editedComment.getContent());
        assertEquals(postId, editedComment.getPostId());
        assertEquals(userId, editedComment.getUserId());
        assertEquals(null, editedComment.getParentId());

        verify(commentRepository).save(any(Comment.class));

    }

    @Test
    void editComment_notFindComment_throwCommentNotFoundException() {
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());
        assertThrows(CommentNotFoundException.class, () -> {
            commentService.editComment(new EditCommentUseCase.Command(commentId, userId, content));
        });
    }

    @Test
    void editComment_notOwner_throwUnauthorizedCommentAccessException() {
        UUID otherUserId = UUID.randomUUID();
        Comment comment = Comment.of(postId, otherUserId, content, null);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        assertThrows(UnauthorizedCommentAccessException.class, () -> {
            commentService.editComment(new EditCommentUseCase.Command(commentId, userId, content));
        });
    }

    @Test
    void deleteComment_owner_deletesComment() {
        Comment comment = Comment.of(postId, userId, content, null);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);

        commentService.deleteComment(new DeleteCommentUseCase.Command(commentId, userId));

        verify(commentRepository).findById(commentId);
        verify(commentRepository).save(any(Comment.class));
        verify(commentRepository).decrementPostCommentCount(postId);
    }

    @Test
    void deleteComment_owner_deletesComment_withReply() {
        Comment comment = Comment.of(postId, userId, content, parentId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);

        commentService.deleteComment(new DeleteCommentUseCase.Command(commentId, userId));

        verify(commentRepository).findById(commentId);
        verify(commentRepository).save(any(Comment.class));
        verify(commentRepository).decrementPostCommentCount(postId);
        verify(commentRepository).decrementReplyCount(parentId);
    }

    @Test
    void deleteComment_notFindComment_throwCommentNotFoundException() {
        when(commentRepository.findById(commentId)).thenReturn(Optional.empty());
        assertThrows(CommentNotFoundException.class, () -> {
            commentService.deleteComment(new DeleteCommentUseCase.Command(commentId, userId));
        });
    }

    @Test
    void deleteComment_notOwner_throwUnauthorizedCommentAccessException() {
        UUID otherUserId = UUID.randomUUID();
        Comment comment = Comment.of(postId, otherUserId, content, null);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));
        assertThrows(UnauthorizedCommentAccessException.class, () -> {
            commentService.deleteComment(new DeleteCommentUseCase.Command(commentId, userId));
        });
    }

}
