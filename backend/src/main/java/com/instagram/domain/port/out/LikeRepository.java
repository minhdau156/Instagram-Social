package com.instagram.domain.port.out;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface LikeRepository {

    // ─── Post Likes ───────────────────────────────────────────────────────────

    /**
     * Persists a new post like.
     *
     * @param postId the ID of the post to like
     * @param userId the ID of the user performing the like
     * @throws com.instagram.domain.exception.AlreadyLikedException if the user has
     *                                                              already liked
     *                                                              this post
     */
    void likePost(UUID postId, UUID userId);

    /**
     * Removes a post like.
     *
     * @param postId the ID of the post to unlike
     * @param userId the ID of the user removing the like
     * @throws com.instagram.domain.exception.NotLikedException if the like does not
     *                                                          exist
     */
    void unlikePost(UUID postId, UUID userId);

    /**
     * Returns true if the given user has already liked the given post.
     *
     * @param postId the ID of the post
     * @param userId the ID of the user
     * @return {@code true} if the like exists; {@code false} otherwise
     */
    boolean hasLikedPost(UUID postId, UUID userId);

    /**
     * Returns a paginated list of user IDs who have liked the post,
     * ordered by most recent first.
     *
     * @param postId   the ID of the post
     * @param pageable pagination parameters
     * @return list of user IDs who liked the post
     */
    Page<UUID> findPostLikerIds(UUID postId, Pageable pageable);

    // ─── Comment Likes ────────────────────────────────────────────────────────

    /**
     * Persists a new comment like.
     *
     * @param commentId the ID of the comment to like
     * @param userId    the ID of the user performing the like
     * @throws com.instagram.domain.exception.AlreadyLikedException if the user has
     *                                                              already liked
     *                                                              this comment
     */
    void likeComment(UUID commentId, UUID userId);

    /**
     * Removes a comment like.
     *
     * @param commentId the ID of the comment to unlike
     * @param userId    the ID of the user removing the like
     * @throws com.instagram.domain.exception.NotLikedException if the like does not
     *                                                          exist
     */
    void unlikeComment(UUID commentId, UUID userId);

    /**
     * Returns true if the given user has already liked the given comment.
     *
     * @param commentId the ID of the comment
     * @param userId    the ID of the user
     * @return {@code true} if the like exists; {@code false} otherwise
     */
    boolean hasLikedComment(UUID commentId, UUID userId);

    Set<UUID> findLikedPostIdsByUserIdAndPostIds(UUID userId, List<UUID> postIds);

    Set<UUID> findLikedCommentIdsByUserIdAndCommentIds(UUID userId, List<UUID> commentIds);
}
