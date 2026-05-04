package com.instagram.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.CommentJpaEntity;
import com.instagram.adapter.out.persistence.repository.CommentJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.domain.model.Comment;
import com.instagram.domain.model.CommentStatus;
import com.instagram.domain.port.out.CommentRepository;

import jakarta.transaction.Transactional;

@Component
public class CommentPersistenceAdapter implements CommentRepository {

    private final CommentJpaRepository commentJpaRepository;
    private final PostJpaRepository postJpaRepository;

    public CommentPersistenceAdapter(CommentJpaRepository commentJpaRepository,
            PostJpaRepository postJpaRepository) {
        this.commentJpaRepository = commentJpaRepository;
        this.postJpaRepository = postJpaRepository;
    }

    @Override
    @Transactional
    public Comment save(Comment comment) {
        CommentJpaEntity entity = toJpaEntity(comment);
        return toDomain(commentJpaRepository.save(entity));
    }

    @Override
    @Transactional
    public Optional<Comment> findById(UUID commentId) {
        return commentJpaRepository.findById(commentId).map(this::toDomain);

    }

    @Override
    @Transactional
    public Page<Comment> findByPostId(UUID postId, Pageable pageable) {
        return commentJpaRepository.findTopLevelByPostId(postId, pageable).map(this::toDomain);
    }

    @Override
    @Transactional
    public Page<Comment> findByParentId(UUID parentId, Pageable pageable) {
        return commentJpaRepository.findRepliesByParentId(parentId, pageable).map(this::toDomain);
    }

    @Override
    @Transactional
    public void incrementReplyCount(UUID parentCommentId) {
        commentJpaRepository.incrementReplyCount(parentCommentId);
    }

    @Override
    @Transactional
    public void decrementReplyCount(UUID parentCommentId) {
        commentJpaRepository.decrementReplyCount(parentCommentId);
    }

    @Override
    @Transactional
    public void incrementLikeCount(UUID commentId) {
        commentJpaRepository.incrementLikeCount(commentId);
    }

    @Override
    @Transactional
    public void decrementLikeCount(UUID commentId) {
        commentJpaRepository.decrementLikeCount(commentId);
    }

    @Override
    @Transactional
    public void incrementPostCommentCount(UUID postId) {
        postJpaRepository.incrementCommentCount(postId);
    }

    @Override
    @Transactional
    public void decrementPostCommentCount(UUID postId) {
        postJpaRepository.decrementCommentCount(postId);
    }

    private Comment toDomain(CommentJpaEntity entity) {
        return Comment.builder()
                .id(entity.getId())
                .postId(entity.getPostId())
                .parentId(entity.getParent() != null ? entity.getParent().getId() : null)
                .userId(entity.getUserId())
                .content(entity.getContent())
                .status(entity.isDeleted() == true ? CommentStatus.DELETED : CommentStatus.ACTIVE)
                .replyCount(entity.getReplyCount())
                .likeCount(entity.getLikeCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private CommentJpaEntity toJpaEntity(Comment comment) {
        CommentJpaEntity parent = null;
        if (comment.getParentId() != null && commentJpaRepository.findById(comment.getParentId()).isPresent()) {
            parent = commentJpaRepository.findById(comment.getParentId()).get();
        }
        return CommentJpaEntity.builder()
                .id(comment.getId())
                .postId(comment.getPostId())
                .parent(parent)
                .userId(comment.getUserId())
                .content(comment.getContent())
                .isDeleted(comment.getStatus() == CommentStatus.DELETED)
                .replyCount(comment.getReplyCount())
                .likeCount(comment.getLikeCount())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }

}
