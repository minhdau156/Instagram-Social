package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostMediaJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserJpaRepository;
import com.instagram.domain.model.MediaType;
import com.instagram.domain.model.PostMedia;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.UserStatus;

public class PostMediaPersistenceAdapterIT extends PostgresIntegrationTest {

    @Autowired
    private PostMediaJpaRepository postMediaJpaRepository;

    @Autowired
    private PostJpaRepository postJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    private PostMediaPersistenceAdapter adapter;

    private PostJpaEntity savedPost;

    @BeforeEach
    void setUp() {
        adapter = new PostMediaPersistenceAdapter(postMediaJpaRepository);
        savedPost = postJpaRepository.save(PostJpaEntity.builder()
                .userId(persistUser())
                .status(PostStatus.PUBLISHED)
                .build());
    }

    // posts.user_id is a real FK under Postgres — every persisted post needs
    // an actually-persisted parent user rather than a bare UUID.randomUUID().
    private UUID persistUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userJpaRepository.save(UserJpaEntity.builder()
                .username("media_user_" + suffix)
                .email("media_user_" + suffix + "@example.com")
                .fullName("Media User")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build()).getId();
    }

    private PostMedia buildMedia(UUID postId, String mediaUrl, String sortOrder) {
        return PostMedia.builder()
                .postId(postId)
                .mediaUrl(mediaUrl)
                .mediaType(MediaType.IMAGE)
                .sortOrder(sortOrder)
                .build();
    }

    @Test
    void saveAll_persistsMediaWithCorrectFields() {
        // given
        PostMedia media = buildMedia(savedPost.getId(), "https://example.com/photo.jpg", "0");

        // when
        List<PostMedia> saved = adapter.saveAll(List.of(media));

        // then
        assertThat(saved).hasSize(1);
        assertNotNull(saved.get(0).getId());
        assertEquals(savedPost.getId(), saved.get(0).getPostId());
        assertEquals("https://example.com/photo.jpg", saved.get(0).getMediaUrl());
        assertEquals(MediaType.IMAGE, saved.get(0).getMediaType());
        assertEquals("0", saved.get(0).getSortOrder());
    }

    @Test
    void saveAll_persistsMultipleMediaItems() {
        // given
        List<PostMedia> mediaList = List.of(
                buildMedia(savedPost.getId(), "https://example.com/photo1.jpg", "0"),
                buildMedia(savedPost.getId(), "https://example.com/photo2.jpg", "1"),
                buildMedia(savedPost.getId(), "https://example.com/photo3.jpg", "2")
        );

        // when
        List<PostMedia> saved = adapter.saveAll(mediaList);

        // then
        assertThat(saved).hasSize(3);
        assertThat(saved).allMatch(m -> m.getPostId().equals(savedPost.getId()));
    }

    @Test
    void findByPostId_returnMediaOrderedBySortOrder() {
        // given
        adapter.saveAll(List.of(
                buildMedia(savedPost.getId(), "https://example.com/photo2.jpg", "1"),
                buildMedia(savedPost.getId(), "https://example.com/photo1.jpg", "0"),
                buildMedia(savedPost.getId(), "https://example.com/photo3.jpg", "2")
        ));

        // when
        List<PostMedia> result = adapter.findByPostId(savedPost.getId());

        // then
        assertThat(result).hasSize(3);
        assertEquals("0", result.get(0).getSortOrder());
        assertEquals("1", result.get(1).getSortOrder());
        assertEquals("2", result.get(2).getSortOrder());
    }

    @Test
    void findByPostId_whenNoMedia_returnsEmpty() {
        // given / when
        List<PostMedia> result = adapter.findByPostId(UUID.randomUUID());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void findByPostIds_returnsMediaForAllGivenPosts() {
        // given
        PostJpaEntity post2 = postJpaRepository.save(PostJpaEntity.builder()
                .userId(persistUser())
                .status(PostStatus.PUBLISHED)
                .build());

        adapter.saveAll(List.of(buildMedia(savedPost.getId(), "https://example.com/a.jpg", "0")));
        adapter.saveAll(List.of(buildMedia(post2.getId(), "https://example.com/b.jpg", "0")));

        // when
        List<PostMedia> result = adapter.findByPostIds(List.of(savedPost.getId(), post2.getId()));

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(PostMedia::getPostId)
                .containsExactlyInAnyOrder(savedPost.getId(), post2.getId());
    }

    @Test
    void findByPostIds_whenNoneMatch_returnsEmpty() {
        // given / when
        List<PostMedia> result = adapter.findByPostIds(List.of(UUID.randomUUID()));

        // then
        assertThat(result).isEmpty();
    }
}
