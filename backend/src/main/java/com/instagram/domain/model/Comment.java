package com.instagram.domain.model;

import java.time.Instant;
import java.util.UUID;

public class Comment {
    private UUID id;
    private UUID postId;
    private UUID userId;
    private UUID parentId; // nullable — null = top-level comment
    private String content;
    private int likeCount;
    private int replyCount;
    private CommentStatus status;
    private Instant createdAt;
    private Instant updatedAt;
    private boolean isLikedByCurrentUser;

    private Comment() {

    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Comment comment = new Comment();

        public Builder id(UUID id) {
            comment.id = id;
            return this;
        }

        public Builder postId(UUID postId) {
            comment.postId = postId;
            return this;
        }

        public Builder userId(UUID userId) {
            comment.userId = userId;
            return this;
        }

        public Builder parentId(UUID parentId) {
            comment.parentId = parentId;
            return this;
        }

        public Builder content(String content) {
            comment.content = content;
            return this;
        }

        public Builder likeCount(int likeCount) {
            comment.likeCount = likeCount;
            return this;
        }

        public Builder replyCount(int replyCount) {
            comment.replyCount = replyCount;
            return this;
        }

        public Builder status(CommentStatus status) {
            comment.status = status;
            return this;
        }

        public Builder createdAt(Instant createdAt) {
            comment.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(Instant updatedAt) {
            comment.updatedAt = updatedAt;
            return this;
        }

        public Builder isLikedByCurrentUser(boolean isLikedByCurrentUser) {
            comment.isLikedByCurrentUser = isLikedByCurrentUser;
            return this;
        }

        public Comment build() {
            if (comment.id == null)
                throw new IllegalArgumentException("id is required");
            if (comment.postId == null)
                throw new IllegalArgumentException("postId is required");
            if (comment.userId == null)
                throw new IllegalArgumentException("userId is required");
            if (comment.content == null)
                throw new IllegalArgumentException("content is required");
            if (comment.status == null)
                throw new IllegalArgumentException("status is required");
            return comment;
        }
    }

    private Builder copy() {
        return builder()
                .id(id)
                .postId(postId)
                .userId(userId)
                .parentId(parentId)
                .content(content)
                .likeCount(likeCount)
                .replyCount(replyCount)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .isLikedByCurrentUser(isLikedByCurrentUser);
    }

    public Comment withEdit(String newContent) {
        return this.copy()
                .content(newContent)
                .updatedAt(Instant.now())
                .build();
    }

    public Comment withSoftDelete() {
        return this.copy()
                .status(CommentStatus.DELETED)
                .content("[deleted]")
                .updatedAt(Instant.now())
                .build();
    }

    public static Comment of(UUID postId, UUID userId, String content, UUID parentId) {
        return builder()
                .id(UUID.randomUUID())
                .postId(postId)
                .userId(userId)
                .parentId(parentId)
                .content(content)
                .likeCount(0)
                .replyCount(0)
                .status(CommentStatus.ACTIVE)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .isLikedByCurrentUser(false)
                .build();
    }

    public UUID getId() {
        return id;
    }

    public UUID getPostId() {
        return postId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getParentId() {
        return parentId;
    }

    public String getContent() {
        return content;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getReplyCount() {
        return replyCount;
    }

    public CommentStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public boolean isLikedByCurrentUser() {
        return isLikedByCurrentUser;
    }

    public Comment withIsLikedByCurrentUser(boolean isLikedByCurrentUser) {
        return this.copy().isLikedByCurrentUser(isLikedByCurrentUser).build();
    }

}
