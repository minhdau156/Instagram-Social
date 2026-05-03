package com.instagram.domain.model;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

public class CommentTest {

    @Test
    void of_createsComment_withCorrectFields() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "test comment";
        UUID parentId = null;

        Comment comment = Comment.of(postId, userId, content, parentId);
        assertThat(comment.getPostId()).isEqualTo(postId);
        assertThat(comment.getUserId()).isEqualTo(userId);
        assertThat(comment.getContent()).isEqualTo(content);
        assertThat(comment.getParentId()).isNull();
        assertThat(comment.getLikeCount()).isEqualTo(0);
        assertThat(comment.getReplyCount()).isEqualTo(0);
        assertThat(comment.getStatus()).isEqualTo(CommentStatus.ACTIVE);
        assertThat(comment.getCreatedAt()).isNotNull();
        assertThat(comment.getUpdatedAt()).isNotNull();
        assertThat(comment.getId()).isNotNull();
    }

    @Test
    void withEdit_returnsNewInstance_withUpdatedContent() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "test comment";
        UUID parentId = null;

        Comment comment = Comment.of(postId, userId, content, parentId);
        Comment updatedComment = comment.withEdit("updated comment");

        assertThat(updatedComment.getContent()).isEqualTo("updated comment");
        assertThat(updatedComment.getUpdatedAt()).isAfterOrEqualTo(comment.getUpdatedAt());
        assertThat(updatedComment.getId()).isEqualTo(comment.getId());
        assertThat(updatedComment.getPostId()).isEqualTo(comment.getPostId());
        assertThat(updatedComment.getUserId()).isEqualTo(comment.getUserId());
        assertThat(updatedComment.getParentId()).isEqualTo(comment.getParentId());
        assertThat(updatedComment.getLikeCount()).isEqualTo(comment.getLikeCount());
        assertThat(updatedComment.getReplyCount()).isEqualTo(comment.getReplyCount());
        assertThat(updatedComment.getStatus()).isEqualTo(comment.getStatus());
        assertThat(updatedComment.getCreatedAt()).isEqualTo(comment.getCreatedAt());
    }

    @Test
    void withSoftDelete_returnsNewInstance_withDeletedStatus() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String content = "test comment";
        UUID parentId = null;

        Comment comment = Comment.of(postId, userId, content, parentId);
        Comment deletedComment = comment.withSoftDelete();

        assertThat(deletedComment.getStatus()).isEqualTo(CommentStatus.DELETED);
        assertThat(deletedComment.getContent()).isEqualTo("[deleted]");
        assertThat(deletedComment.getId()).isEqualTo(comment.getId());
        assertThat(deletedComment.getPostId()).isEqualTo(comment.getPostId());
        assertThat(deletedComment.getUserId()).isEqualTo(comment.getUserId());
        assertThat(deletedComment.getParentId()).isEqualTo(comment.getParentId());
        assertThat(deletedComment.getLikeCount()).isEqualTo(comment.getLikeCount());
        assertThat(deletedComment.getReplyCount()).isEqualTo(comment.getReplyCount());
        assertThat(deletedComment.getCreatedAt()).isEqualTo(comment.getCreatedAt());
    }

}
