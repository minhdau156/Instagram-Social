package com.instagram.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.instagram.domain.event.NotificationEvent;
import com.instagram.domain.exception.CommentNotFoundException;
import com.instagram.domain.exception.PostNotFoundException;
import com.instagram.domain.exception.UnauthorizedCommentAccessException;
import com.instagram.domain.exception.UserNotFoundException;
import com.instagram.domain.model.Comment;
import com.instagram.domain.model.Notification;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.User;
import com.instagram.domain.port.in.comment.AddCommentUseCase;
import com.instagram.domain.port.in.comment.DeleteCommentUseCase;
import com.instagram.domain.port.in.comment.EditCommentUseCase;
import com.instagram.domain.port.in.comment.GetCommentsUseCase;
import com.instagram.domain.port.in.comment.GetRepliesUseCase;
import com.instagram.domain.port.out.CommentRepository;
import com.instagram.domain.port.out.LikeRepository;
import com.instagram.domain.port.out.PostRepository;
import com.instagram.domain.port.out.UserInterestPort;
import com.instagram.domain.port.out.UserRepository;
import com.instagram.infrastructure.util.CursorEncoder;

import jakarta.transaction.Transactional;

@Service
public class CommentService implements AddCommentUseCase, EditCommentUseCase,
        DeleteCommentUseCase,
        GetCommentsUseCase,
        GetRepliesUseCase {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final LikeRepository likeRepository;
    private final UserInterestPort userInterestPort;
    private final ApplicationEventPublisher eventPublisher;

    public CommentService(CommentRepository commentRepository, UserRepository userRepository,
            PostRepository postRepository, LikeRepository likeRepository,
            UserInterestPort userInterestPort, ApplicationEventPublisher eventPublisher) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.likeRepository = likeRepository;
        this.userInterestPort = userInterestPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional
    public Page<Comment> getReplies(GetRepliesUseCase.Query query) {
        Page<Comment> comments = commentRepository
                .findByParentId(query.commentId(), PageRequest.of(query.page(), query.size()));
        User currentUser = userRepository.findById(query.currentUserId())
                .orElseThrow(() -> new UserNotFoundException(query.currentUserId().toString()));

        List<UUID> commentIds = comments.getContent().stream()
                .map(Comment::getId)
                .toList();
        Set<UUID> likedCommentIds = this.likeRepository.findLikedCommentIdsByUserIdAndCommentIds(query.currentUserId(),
                commentIds);
        return comments.map(comment -> {
            boolean isLikedByCurrentUser = likedCommentIds.contains(comment.getId());
            return comment.withIsLikedByCurrentUser(isLikedByCurrentUser);
        });
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "comments", key = "'comments:' + @commentService.getPostId(#command.commentId) + ':page1'"),

    })
    public void deleteComment(DeleteCommentUseCase.Command command) {
        Comment comment = this.commentRepository.findById(command.commentId())
                .orElseThrow(() -> new CommentNotFoundException(command.commentId()));

        if (!comment.getUserId().equals(command.userId())) {
            throw new UnauthorizedCommentAccessException(comment.getId(), command.userId());
        }
        Comment deleteComment = comment.withSoftDelete();
        this.commentRepository.save(deleteComment);
        if (comment.getParentId() != null) {
            this.commentRepository.decrementReplyCount(comment.getParentId());
        }
        this.commentRepository.decrementPostCommentCount(comment.getPostId());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "comments", key = "'comments:' + @commentService.getPostId(#command.commentId) + ':page1'"),

    })
    public Comment editComment(EditCommentUseCase.Command command) {
        Comment comment = this.commentRepository.findById(command.commentId())
                .orElseThrow(() -> new CommentNotFoundException(command.commentId()));

        if (!comment.getUserId().equals(command.userId())) {
            throw new UnauthorizedCommentAccessException(comment.getId(), command.userId());
        }
        Comment editComment = comment.withEdit(command.newContent());
        this.commentRepository.save(editComment);

        List<String> mentions = extractMentions(command.newContent());
        if (!mentions.isEmpty()) {
            log.info("Extracted mentions in edited comment {}: {}", editComment.getId(), mentions);
        }

        return editComment;
    }

    public UUID getPostId(UUID commentId) {
        Comment comment = this.commentRepository.findById(commentId)
                .orElseThrow(() -> new CommentNotFoundException(commentId));
        return comment.getPostId();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "comments", key = "'comments:' + #command.postId + ':page1'"),

    })
    public Comment addComment(AddCommentUseCase.Command command) {
        Comment comment = Comment.of(command.postId(), command.userId(), command.content(), command.parentId());
        Comment newComment = this.commentRepository.save(comment);
        if (command.parentId() != null) {
            this.commentRepository.incrementReplyCount(command.parentId());
        }
        this.commentRepository.incrementPostCommentCount(command.postId());
        this.userInterestPort.recordComment(command.userId(), command.postId());

        Post post = postRepository.findById(command.postId())
                .orElseThrow(() -> new PostNotFoundException(command.postId()));
        if (!post.getUserId().equals(command.userId())) {
            eventPublisher.publishEvent(new NotificationEvent(
                    this,
                    Notification.NotificationType.COMMENT_POST,
                    post.getUserId(),
                    command.userId(),
                    Notification.EntityType.POST,
                    command.postId()));
        }

        List<String> mentions = extractMentions(command.content());
        List<User> mentionedUsers = userRepository.findByUsernames(mentions);

        for (User mentionedUser : mentionedUsers) {

            if (!mentionedUser.getId().equals(command.userId())) {
                eventPublisher.publishEvent(new NotificationEvent(
                        this,
                        Notification.NotificationType.MENTION_COMMENT,
                        mentionedUser.getId(),
                        command.userId(),
                        Notification.EntityType.COMMENT,
                        comment.getId()));
            }
        }
        return newComment;
    }

    @Override
    @Transactional
    @Cacheable(value = "comments", key = "'comments:' + #query.postId + ':page1'", condition = "#query.cursor() == null")
    public List<Comment> getComments(GetCommentsUseCase.Query query) {
        CursorEncoder.DecodedCursor decoded = query.cursor() != null
                ? CursorEncoder.decode(query.cursor())
                : null;
        String cursorTs = decoded != null ? decoded.createdAt().toString() : null;
        UUID cursorId = decoded != null ? decoded.id() : null;

        List<Comment> comments = commentRepository
                .findByPostId(query.postId(), cursorTs, cursorId, query.size());

        User currentUser = userRepository.findById(query.currentUserId())
                .orElseThrow(() -> new UserNotFoundException(query.currentUserId().toString()));
        List<UUID> commentIds = comments.stream()
                .map(Comment::getId)
                .toList();
        Set<UUID> likedCommentIds = this.likeRepository.findLikedCommentIdsByUserIdAndCommentIds(query.currentUserId(),
                commentIds);

        return comments.stream().map(comment -> {
            boolean isLikedByCurrentUser = likedCommentIds.contains(comment.getId());
            return comment.copy()
                    .isLikedByCurrentUser(isLikedByCurrentUser)
                    .build();
        }).toList();
    }

    private List<String> extractMentions(String content) {
        Pattern pattern = Pattern.compile("@(\\w+)");
        Matcher matcher = pattern.matcher(content);
        List<String> mentions = new ArrayList<>();
        while (matcher.find()) {
            mentions.add(matcher.group(1));
        }
        return mentions;
    }

}
