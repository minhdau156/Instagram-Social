package com.instagram.adapter.out.persistence.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityStructureTest {

    // ── PostLikeJpaEntity ─────────────────────────────────────────────────────

    @Test
    void postLikeEntity_equalWhenSameCompositeId() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PostLikeId id = new PostLikeId(postId, userId);

        PostLikeJpaEntity e1 = new PostLikeJpaEntity(id);
        PostLikeJpaEntity e2 = new PostLikeJpaEntity(id);

        assertThat(e1).isEqualTo(e2);
    }

    @Test
    void postLikeEntity_notEqualWhenDifferentId() {
        PostLikeJpaEntity e1 = new PostLikeJpaEntity(new PostLikeId(UUID.randomUUID(), UUID.randomUUID()));
        PostLikeJpaEntity e2 = new PostLikeJpaEntity(new PostLikeId(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(e1).isNotEqualTo(e2);
    }

    @Test
    void postLikeEntity_sameHashCodeForEqualInstances() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PostLikeId id = new PostLikeId(postId, userId);

        assertThat(new PostLikeJpaEntity(id).hashCode())
                .isEqualTo(new PostLikeJpaEntity(id).hashCode());
    }

    @Test
    void postLikeEntity_setterUpdatesId() {
        PostLikeJpaEntity entity = new PostLikeJpaEntity();
        PostLikeId id = new PostLikeId(UUID.randomUUID(), UUID.randomUUID());
        entity.setId(id);
        assertThat(entity.getId()).isEqualTo(id);
    }

    // ── CommentLikeJpaEntity ──────────────────────────────────────────────────

    @Test
    void commentLikeEntity_equalWhenSameCompositeId() {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        CommentLikeId id = new CommentLikeId(commentId, userId);

        CommentLikeJpaEntity e1 = new CommentLikeJpaEntity(id);
        CommentLikeJpaEntity e2 = new CommentLikeJpaEntity(id);

        assertThat(e1).isEqualTo(e2);
    }

    @Test
    void commentLikeEntity_notEqualWhenDifferentId() {
        CommentLikeJpaEntity e1 = new CommentLikeJpaEntity(
                new CommentLikeId(UUID.randomUUID(), UUID.randomUUID()));
        CommentLikeJpaEntity e2 = new CommentLikeJpaEntity(
                new CommentLikeId(UUID.randomUUID(), UUID.randomUUID()));

        assertThat(e1).isNotEqualTo(e2);
    }

    @Test
    void commentLikeEntity_sameHashCodeForEqualInstances() {
        CommentLikeId id = new CommentLikeId(UUID.randomUUID(), UUID.randomUUID());

        assertThat(new CommentLikeJpaEntity(id).hashCode())
                .isEqualTo(new CommentLikeJpaEntity(id).hashCode());
    }

    @Test
    void commentLikeEntity_setterUpdatesId() {
        CommentLikeJpaEntity entity = new CommentLikeJpaEntity();
        CommentLikeId id = new CommentLikeId(UUID.randomUUID(), UUID.randomUUID());
        entity.setId(id);
        assertThat(entity.getId()).isEqualTo(id);
    }

    // ── SavedPostJpaEntity ────────────────────────────────────────────────────

    @Test
    void savedPostEntity_defaultConstructorWorks() {
        SavedPostJpaEntity entity = new SavedPostJpaEntity();
        assertThat(entity.getId()).isNull();
        assertThat(entity.getSavedAt()).isNull();
    }

    @Test
    void savedPostEntity_setterUpdatesId() {
        SavedPostJpaEntity entity = new SavedPostJpaEntity();
        SavePostId id = new SavePostId(UUID.randomUUID(), UUID.randomUUID());
        entity.setId(id);
        assertThat(entity.getId()).isEqualTo(id);
    }

    // ── UserStatsJpaEntity ────────────────────────────────────────────────────

    @Test
    void userStatsEntity_builder_setsAllFields() {
        UUID userId = UUID.randomUUID();
        UserStatsJpaEntity stats = UserStatsJpaEntity.builder()
                .userId(userId)
                .followerCount(100L)
                .followingCount(50L)
                .postCount(30L)
                .build();

        assertThat(stats.getUserId()).isEqualTo(userId);
        assertThat(stats.getFollowerCount()).isEqualTo(100L);
        assertThat(stats.getFollowingCount()).isEqualTo(50L);
        assertThat(stats.getPostCount()).isEqualTo(30L);
    }

    @Test
    void userStatsEntity_setterUpdatesFields() {
        UserStatsJpaEntity stats = new UserStatsJpaEntity();
        UUID userId = UUID.randomUUID();
        stats.setUserId(userId);
        stats.setFollowerCount(10L);
        stats.setFollowingCount(5L);
        stats.setPostCount(3L);

        assertThat(stats.getUserId()).isEqualTo(userId);
        assertThat(stats.getFollowerCount()).isEqualTo(10L);
    }

    // ── NotificationSettingsJpaEntity ─────────────────────────────────────────

    @Test
    void notificationSettingsEntity_builder_setsAllFields() {
        UUID userId = UUID.randomUUID();
        NotificationSettingsJpaEntity settings = NotificationSettingsJpaEntity.builder()
                .userId(userId)
                .likesEnabled(true)
                .commentsEnabled(true)
                .followsEnabled(false)
                .messagesEnabled(true)
                .pushEnabled(false)
                .build();

        assertThat(settings.getUserId()).isEqualTo(userId);
        assertThat(settings.isLikesEnabled()).isTrue();
        assertThat(settings.isCommentsEnabled()).isTrue();
        assertThat(settings.isFollowsEnabled()).isFalse();
        assertThat(settings.isMessagesEnabled()).isTrue();
        assertThat(settings.isPushEnabled()).isFalse();
    }

    @Test
    void notificationSettingsEntity_defaultConstructorCreatesNullFields() {
        NotificationSettingsJpaEntity settings = new NotificationSettingsJpaEntity();
        assertThat(settings.getUserId()).isNull();
    }

    // ── HashtagJpaEntity ──────────────────────────────────────────────────────

    @Test
    void hashtagEntity_builder_setsAllFields() {
        UUID id = UUID.randomUUID();
        HashtagJpaEntity hashtag = HashtagJpaEntity.builder()
                .id(id)
                .name("spring")
                .postCount(42)
                .build();

        assertThat(hashtag.getId()).isEqualTo(id);
        assertThat(hashtag.getName()).isEqualTo("spring");
        assertThat(hashtag.getPostCount()).isEqualTo(42);
    }

    @Test
    void hashtagEntity_setterUpdatesPostCount() {
        HashtagJpaEntity hashtag = new HashtagJpaEntity();
        hashtag.setPostCount(100);
        assertThat(hashtag.getPostCount()).isEqualTo(100);
    }

    // ── UserJpaEntity ─────────────────────────────────────────────────────────

    @Test
    void userEntity_builderDefaultsVerifiedToFalse() {
        UserJpaEntity user = UserJpaEntity.builder()
                .username("john")
                .email("john@example.com")
                .fullName("John Doe")
                .build();

        assertThat(user.isVerified()).isFalse();
    }

    @Test
    void userEntity_setterUpdatesVerified() {
        UserJpaEntity user = new UserJpaEntity();
        user.setVerified(true);
        assertThat(user.isVerified()).isTrue();
    }
}
