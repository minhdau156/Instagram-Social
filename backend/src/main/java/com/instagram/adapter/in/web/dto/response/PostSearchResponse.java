package com.instagram.adapter.in.web.dto.response;

import java.util.List;

import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostMedia;
import com.instagram.domain.model.User;

public record PostSearchResponse(
        String id,
        String authorUsername,
        String authorAvatarUrl,
        String caption,
        String mediaUrl,
        String mediaType,
        int likeCount,
        int commentCount,
        String createdAt) {

    public static PostSearchResponse from(Post post, User author, List<PostMedia> media) {
        return new PostSearchResponse(
                post.getId().toString(),
                author.getUsername(),
                author.getProfilePictureUrl(),
                post.getCaption(),
                media.isEmpty() ? null : media.get(0).getMediaUrl(),
                media.isEmpty() ? null : media.get(0).getMediaType().name(),
                post.getLikeCount(),
                post.getCommentCount(),
                post.getCreatedAt().toString());
    }
}
