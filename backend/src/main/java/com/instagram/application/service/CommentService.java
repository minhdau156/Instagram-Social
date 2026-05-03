package com.instagram.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.instagram.domain.exception.CommentNotFoundException;
import com.instagram.domain.exception.UnauthorizedCommentAccessException;
import com.instagram.domain.model.Comment;
import com.instagram.domain.port.in.comment.AddCommentUseCase;
import com.instagram.domain.port.in.comment.DeleteCommentUseCase;
import com.instagram.domain.port.in.comment.EditCommentUseCase;
import com.instagram.domain.port.in.comment.GetCommentsUseCase;
import com.instagram.domain.port.in.comment.GetRepliesUseCase;
import com.instagram.domain.port.out.CommentRepository;
import com.instagram.domain.port.out.UserRepository;

@Service
public class CommentService implements AddCommentUseCase, EditCommentUseCase,
        DeleteCommentUseCase,
        GetCommentsUseCase,
        GetRepliesUseCase {

    private static final Logger log = LoggerFactory.getLogger(CommentService.class);

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;

    public CommentService(CommentRepository commentRepository, UserRepository userRepository) {
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<Comment> getReplies(GetRepliesUseCase.Query query) {
        return commentRepository.findByParentId(query.commentId(), PageRequest.of(query.page(), query.size()))
                .getContent();
    }

    @Override
    public void deleteComment(DeleteCommentUseCase.Command command) {
        Comment comment = this.commentRepository.findById(command.commentId())
                .orElseThrow(() -> new CommentNotFoundException(command.commentId()));

        if (!comment.getUserId().equals(command.userId())) {
            throw new UnauthorizedCommentAccessException(comment.getId(), comment.getUserId());
        }
        Comment deleteComment = comment.withSoftDelete();
        this.commentRepository.save(deleteComment);
        if (comment.getParentId() != null) {
            this.commentRepository.decrementReplyCount(comment.getParentId());
        }
        this.commentRepository.decrementPostCommentCount(comment.getPostId());
    }

    @Override
    public Comment editComment(EditCommentUseCase.Command command) {
        Comment comment = this.commentRepository.findById(command.commentId())
                .orElseThrow(() -> new CommentNotFoundException(command.commentId()));

        if (!comment.getUserId().equals(command.userId())) {
            throw new UnauthorizedCommentAccessException(comment.getId(), comment.getUserId());
        }
        Comment editComment = comment.withEdit(command.newContent());
        this.commentRepository.save(editComment);
        
        List<String> mentions = extractMentions(command.newContent());
        if (!mentions.isEmpty()) {
            log.info("Extracted mentions in edited comment {}: {}", editComment.getId(), mentions);
        }

        return editComment;
    }

    @Override
    public Comment addComment(AddCommentUseCase.Command command) {
        Comment newComment = Comment.of(command.postId(), command.userId(), command.content(), command.parentId());
        this.commentRepository.save(newComment);
        if (command.parentId() != null) {
            this.commentRepository.incrementReplyCount(command.parentId());
        }
        this.commentRepository.incrementPostCommentCount(command.postId());

        List<String> mentions = extractMentions(command.content());
        if (!mentions.isEmpty()) {
            log.info("Extracted mentions in new comment {}: {}", newComment.getId(), mentions);
        }

        return newComment;
    }

    @Override
    public List<Comment> getComments(GetCommentsUseCase.Query query) {
        return commentRepository.findByPostId(query.postId(), PageRequest.of(query.page(), query.size())).getContent();
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
