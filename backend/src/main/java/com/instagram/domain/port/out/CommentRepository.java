package com.instagram.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.instagram.domain.model.Comment;

public interface CommentRepository {
    /**
     * Persists a new comment or saves an updated comment (edit/soft-delete).
     */
    Comment save(Comment comment);

    /**
     * Finds a comment by its surrogate ID. Returns empty if not found or DELETED.
     */
    Optional<Comment> findById(UUID commentId);

    /**
     * Returns paginated top-level comments for a post (parentId IS NULL and status
     * != DELETED),
     * ordered by createdAt ascending.
     */
    List<Comment> findByPostId(UUID postId, String cursorTs, UUID cursorId, int size);

    /**
     * Returns paginated replies for a given parent comment (parentId = parentId and
     * status != DELETED),
     * ordered by createdAt ascending.
     */
    Page<Comment> findByParentId(UUID parentId, Pageable pageable);

    /**
     * Increments the reply_count counter for the parent comment.
     * Called when a new reply is added.
     */
    void incrementReplyCount(UUID parentCommentId);

    /**
     * Decrements the reply_count counter for the parent comment.
     * Called when a reply is deleted.
     */
    void decrementReplyCount(UUID parentCommentId);

    /**
     * Increments the like_count for a comment.
     */
    void incrementLikeCount(UUID commentId);

    /**
     * Decrements the like_count for a comment, minimum 0.
     */
    void decrementLikeCount(UUID commentId);

    /**
     * Increments the comment_count on the parent post.
     */
    void incrementPostCommentCount(UUID postId);

    /**
     * Decrements the comment_count on the parent post.
     */
    void decrementPostCommentCount(UUID postId);

}
