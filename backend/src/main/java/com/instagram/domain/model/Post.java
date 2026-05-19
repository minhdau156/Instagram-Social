package com.instagram.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Core domain entity representing a user post.
 *
 * <p>
 * Pure Java — no Spring, JPA, or Lombok annotations allowed here.
 * The domain model is the heart of the hexagonal architecture.
 * </p>
 */
public class Post {

    private UUID id; // null before first persistence
    private UUID userId;
    private String caption;
    private String location;

    private PostStatus status;
    private long viewCount;
    private int likeCount;
    private int commentCount;
    private int saveCount;
    private int shareCount;
    private boolean likedByCurrentUser;
    private boolean savedByCurrentUser;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;

    /** Use {@link Post#builder()} for construction. */
    private Post() {
    }

    // ── Getters ──────────────────────────────────────────────────────────── //

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getCaption() {
        return caption;
    }

    public String getLocation() {
        return location;
    }

    public PostStatus getStatus() {
        return status;
    }

    public long getViewCount() {
        return viewCount;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getCommentCount() {
        return commentCount;
    }

    public int getSaveCount() {
        return saveCount;
    }

    public int getShareCount() {
        return shareCount;
    }

    public boolean isLikedByCurrentUser() {
        return likedByCurrentUser;
    }

    public boolean isSavedByCurrentUser() {
        return savedByCurrentUser;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    // ── Domain Behaviour ─────────────────────────────────────────────────── //

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isPublished() {
        return PostStatus.PUBLISHED == status;
    }

    public void setLikedByCurrentUser(boolean likedByCurrentUser) {
        this.likedByCurrentUser = likedByCurrentUser;
    }

    /**
     * Apply an edit to caption and/or location.
     * Returns a new Post with updated fields and refreshed timestamp.
     */
    public Post withUpdateCaptionAndLocation(String caption, String location) {
        Builder builder = this.copy();
        if (caption != null) {
            builder.caption(caption);
        }
        if (location != null) {
            builder.location(location);
        }
        return builder.updatedAt(OffsetDateTime.now()).build();
    }

    /** Mark the post as soft-deleted. */
    public Post withSoftDelete() {
        return this.copy()
                .status(PostStatus.DELETED)
                .deletedAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
    }

    public Post withIncrementLikeCount() {
        return this.copy().likeCount(this.likeCount + 1).updatedAt(OffsetDateTime.now()).build();
    }

    public Post withDecrementLikeCount() {

        return this.copy().likeCount(this.likeCount - 1).updatedAt(OffsetDateTime.now()).build();
    }

    public Builder copy() {
        Builder p = new Builder();
        p.id(id)
                .userId(userId)
                .caption(caption)
                .location(location)
                .status(status)
                .viewCount(viewCount)
                .likeCount(likeCount)
                .commentCount(commentCount)
                .saveCount(saveCount)
                .shareCount(shareCount)
                .likedByCurrentUser(likedByCurrentUser)
                .savedByCurrentUser(savedByCurrentUser)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .deletedAt(deletedAt);
        return p;
    }

    // ── Builder ──────────────────────────────────────────────────────────── //

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Post post = new Post();

        public Builder id(UUID id) {
            post.id = id;
            return this;
        }

        public Builder userId(UUID userId) {
            post.userId = userId;
            return this;
        }

        public Builder caption(String caption) {
            post.caption = caption;
            return this;
        }

        public Builder location(String location) {
            post.location = location;
            return this;
        }

        public Builder status(PostStatus status) {
            post.status = status;
            return this;
        }

        public Builder viewCount(long viewCount) {
            post.viewCount = viewCount;
            return this;
        }

        public Builder likeCount(int likeCount) {
            post.likeCount = likeCount;
            return this;
        }

        public Builder commentCount(int commentCount) {
            post.commentCount = commentCount;
            return this;
        }

        public Builder saveCount(int saveCount) {
            post.saveCount = saveCount;
            return this;
        }

        public Builder shareCount(int shareCount) {
            post.shareCount = shareCount;
            return this;
        }

        public Builder likedByCurrentUser(boolean likedByCurrentUser) {
            post.likedByCurrentUser = likedByCurrentUser;
            return this;
        }

        public Builder savedByCurrentUser(boolean savedByCurrentUser) {
            post.savedByCurrentUser = savedByCurrentUser;
            return this;
        }

        public Builder createdAt(OffsetDateTime createdAt) {
            post.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(OffsetDateTime updatedAt) {
            post.updatedAt = updatedAt;
            return this;
        }

        public Builder deletedAt(OffsetDateTime deletedAt) {
            post.deletedAt = deletedAt;
            return this;
        }

        public Post build() {
            return post;
        }
    }

}
