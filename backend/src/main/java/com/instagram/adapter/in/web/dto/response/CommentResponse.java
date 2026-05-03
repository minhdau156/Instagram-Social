package com.instagram.adapter.in.web.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.instagram.domain.model.Comment;
import com.instagram.domain.model.CommentStatus;
import com.instagram.domain.model.User;

public record CommentResponse(
        UUID id,
        UUID postId,
        UUID userId,
        String username,
        String avatarUrl,
        UUID parentId,
        String content,
        int likeCount,
        int replyCount,
        CommentStatus status,
        Instant createdAt,
        Instant updatedAt,
        boolean isLikedByCurrentUser // populated by the service layer; false if unauthenticated
) {
    public static CommentResponse from(Comment comment, User user) {
        return new CommentResponse(
                comment.getId(),
                comment.getPostId(),
                comment.getUserId(),
                user.getUsername(),
                user.getProfilePictureUrl(),
                comment.getParentId(),
                comment.getContent(),
                comment.getLikeCount(),
                comment.getReplyCount(),
                comment.getStatus(),
                comment.getCreatedAt(),
                comment.getUpdatedAt(),
                comment.isLikedByCurrentUser());
    }
}
