package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.entity.SavePostId;
import com.instagram.adapter.out.persistence.entity.SavedPostJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.adapter.out.persistence.repository.SavedPostJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserJpaRepository;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.SavedPost;
import com.instagram.domain.model.UserStatus;

@Import(SavedPostPersistenceAdapter.class)
class SavedPostPersistenceAdapterIT extends PostgresIntegrationTest {

    @Autowired
    private SavedPostPersistenceAdapter adapter;

    @Autowired
    private SavedPostJpaRepository savedPostJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PostJpaRepository postJpaRepository;

    @BeforeEach
    void setUp() {
        savedPostJpaRepository.deleteAll();
    }

    // saved_posts.user_id / saved_posts.post_id are real FKs — every test that
    // inserts a row needs an actually-persisted parent rather than a bare
    // UUID.randomUUID().
    private UUID persistUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userJpaRepository.save(UserJpaEntity.builder()
                .username("saver_" + suffix)
                .email("saver_" + suffix + "@example.com")
                .fullName("Saver")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build()).getId();
    }

    private UUID persistPost(UUID userId) {
        return postJpaRepository.save(PostJpaEntity.builder()
                .userId(userId)
                .status(PostStatus.PUBLISHED)
                .build()).getId();
    }

    @Test
    void save_persistsSavedPostRecord() {
        UUID userId = persistUser();
        UUID postId = persistPost(userId);

        SavedPost result = adapter.save(SavedPost.of(postId, userId));

        assertThat(result.getPostId()).isEqualTo(postId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getSavedAt()).isNotNull();
        assertThat(adapter.existsByPostIdAndUserId(postId, userId)).isTrue();
    }

    @Test
    void delete_removesSavedPostRecord() {
        UUID userId = persistUser();
        UUID postId = persistPost(userId);

        adapter.save(SavedPost.of(postId, userId));
        assertThat(adapter.existsByPostIdAndUserId(postId, userId)).isTrue();

        adapter.delete(postId, userId);

        assertThat(adapter.existsByPostIdAndUserId(postId, userId)).isFalse();
    }

    @Test
    void existsByPostIdAndUserId_returnsFalseWhenNotSaved() {
        // Pure read with no row inserted — no FK to satisfy, bare UUIDs are fine.
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThat(adapter.existsByPostIdAndUserId(postId, userId)).isFalse();
    }

    @Test
    void findByUserId_returnsSavedPostsOrderedBySavedAtDesc() throws InterruptedException {
        UUID userId = persistUser();
        UUID postId1 = persistPost(userId);
        UUID postId2 = persistPost(userId);

        // Insert first record
        SavedPostJpaEntity first = new SavedPostJpaEntity(
                new SavePostId(postId1, userId),
                Instant.now().minusSeconds(5));
        savedPostJpaRepository.save(first);

        // Insert second record (more recent)
        SavedPostJpaEntity second = new SavedPostJpaEntity(
                new SavePostId(postId2, userId),
                Instant.now());
        savedPostJpaRepository.save(second);

        Page<SavedPost> result = adapter.findByUserId(userId, PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        // Descending order: most recent first
        assertThat(result.getContent().get(0).getPostId()).isEqualTo(postId2);
        assertThat(result.getContent().get(1).getPostId()).isEqualTo(postId1);
    }
}
