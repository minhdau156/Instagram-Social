package com.instagram.adapter.in.web.dto.response;

import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostMedia;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Read-only response DTO for post data.
 * Constructed from a domain {@link Post} via the static factory
 * {@link #from(Post)}.
 */
public record PostResponse(
        UUID id,
        UUID userId,
        String caption,
        String location,
        String status,
        long viewCount,
        int likeCount,
        int commentCount,
        int saveCount,
        int shareCount,
        boolean likedByCurrentUser,
        boolean savedByCurrentUser,
        String createdAt,
        String updatedAt,
        List<MediaItemResponse> mediaItems) {
    public static PostResponse from(Post post, List<PostMedia> postMedias) {
        return new PostResponse(
                post.getId(),
                post.getUserId(),
                post.getCaption(),
                post.getLocation(),
                post.getStatus().name(),
                post.getViewCount(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getSaveCount(),
                post.getShareCount(),
                post.isLikedByCurrentUser(),
                post.isSavedByCurrentUser(),
                post.getCreatedAt().toString(),
                post.getUpdatedAt().toString(),
                postMedias == null ? List.of() : postMedias.stream().map(MediaItemResponse::from).toList());
    }
}
