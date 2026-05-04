package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import com.instagram.adapter.out.persistence.entity.SavePostId;
import com.instagram.adapter.out.persistence.entity.SavedPostJpaEntity;
import com.instagram.adapter.out.persistence.repository.SavedPostJpaRepository;
import com.instagram.domain.model.SavedPost;

@DataJpaTest
@Import(SavedPostPersistenceAdapter.class)
@TestPropertySource(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop" })
class SavedPostPersistenceAdapterIT {

    @Autowired
    private SavedPostPersistenceAdapter adapter;

    @Autowired
    private SavedPostJpaRepository savedPostJpaRepository;

    @BeforeEach
    void setUp() {
        savedPostJpaRepository.deleteAll();
    }

    @Test
    void save_persistsSavedPostRecord() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        SavedPost result = adapter.save(SavedPost.of(postId, userId));

        assertThat(result.getPostId()).isEqualTo(postId);
        assertThat(result.getUserId()).isEqualTo(userId);
        assertThat(result.getSavedAt()).isNotNull();
        assertThat(adapter.existsByPostIdAndUserId(postId, userId)).isTrue();
    }

    @Test
    void delete_removesSavedPostRecord() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        adapter.save(SavedPost.of(postId, userId));
        assertThat(adapter.existsByPostIdAndUserId(postId, userId)).isTrue();

        adapter.delete(postId, userId);

        assertThat(adapter.existsByPostIdAndUserId(postId, userId)).isFalse();
    }

    @Test
    void existsByPostIdAndUserId_returnsFalseWhenNotSaved() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThat(adapter.existsByPostIdAndUserId(postId, userId)).isFalse();
    }

    @Test
    void findByUserId_returnsSavedPostsOrderedBySavedAtDesc() throws InterruptedException {
        UUID userId = UUID.randomUUID();
        UUID postId1 = UUID.randomUUID();
        UUID postId2 = UUID.randomUUID();

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
