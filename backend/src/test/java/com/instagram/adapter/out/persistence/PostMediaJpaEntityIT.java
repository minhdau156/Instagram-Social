package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.entity.PostMediaJpaEntity;
import com.instagram.domain.model.MediaType;
import com.instagram.domain.model.PostStatus;
import com.instagram.infrastructure.config.JpaConfig;

@DataJpaTest
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PostMediaJpaEntityIT {

    @Autowired
    private TestEntityManager entityManager;

    private PostJpaEntity savedPost;

    @BeforeEach
    void setUp() {
        savedPost = entityManager.persistAndFlush(PostJpaEntity.builder()
                .userId(UUID.randomUUID())
                .status(PostStatus.PUBLISHED)
                .build());
    }

    private PostMediaJpaEntity minimalMedia(PostJpaEntity post, short sortOrder) {
        return PostMediaJpaEntity.builder()
                .post(post)
                .mediaUrl("https://cdn.example.com/image.jpg")
                .mediaType(MediaType.IMAGE)
                .sortOrder(sortOrder)
                .build();
    }

    @Test
    void shouldPersistAndRetrieveWithRequiredFields() {
        PostMediaJpaEntity saved = entityManager.persistFlushFind(minimalMedia(savedPost, (short) 0));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPost().getId()).isEqualTo(savedPost.getId());
        assertThat(saved.getMediaUrl()).isEqualTo("https://cdn.example.com/image.jpg");
        assertThat(saved.getMediaType()).isEqualTo(MediaType.IMAGE);
        assertThat(saved.getSortOrder()).isEqualTo((short) 0);
    }

    @Test
    void shouldAutoPopulateCreatedAt() {
        PostMediaJpaEntity saved = entityManager.persistFlushFind(minimalMedia(savedPost, (short) 0));

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldPersistAllMediaTypeEnumValues() {
        for (MediaType type : MediaType.values()) {
            PostMediaJpaEntity media = PostMediaJpaEntity.builder()
                    .post(savedPost)
                    .mediaUrl("https://example.com/" + type.name().toLowerCase())
                    .mediaType(type)
                    .sortOrder((short) 0)
                    .build();

            PostMediaJpaEntity saved = entityManager.persistFlushFind(media);
            assertThat(saved.getMediaType()).isEqualTo(type);
        }
    }

    @Test
    void shouldPersistOptionalFields() {
        PostMediaJpaEntity media = PostMediaJpaEntity.builder()
                .post(savedPost)
                .mediaUrl("https://example.com/video.mp4")
                .mediaType(MediaType.VIDEO)
                .thumbnailUrl("https://example.com/thumb.jpg")
                .width(1920)
                .height(1080)
                .durationSecs(new BigDecimal("60.50"))
                .fileSizeBytes(10_485_760L)
                .sortOrder((short) 0)
                .build();

        PostMediaJpaEntity saved = entityManager.persistFlushFind(media);

        assertThat(saved.getThumbnailUrl()).isEqualTo("https://example.com/thumb.jpg");
        assertThat(saved.getWidth()).isEqualTo(1920);
        assertThat(saved.getHeight()).isEqualTo(1080);
        assertThat(saved.getDurationSecs()).isEqualByComparingTo(new BigDecimal("60.50"));
        assertThat(saved.getFileSizeBytes()).isEqualTo(10_485_760L);
    }

    @Test
    void shouldAllowNullableOptionalFields() {
        PostMediaJpaEntity saved = entityManager.persistFlushFind(minimalMedia(savedPost, (short) 0));

        assertThat(saved.getThumbnailUrl()).isNull();
        assertThat(saved.getWidth()).isNull();
        assertThat(saved.getHeight()).isNull();
        assertThat(saved.getDurationSecs()).isNull();
        assertThat(saved.getFileSizeBytes()).isNull();
    }

    @Test
    void shouldSupportMultipleMediaItemsForOnePost() {
        entityManager.persistAndFlush(minimalMedia(savedPost, (short) 0));
        entityManager.persistAndFlush(minimalMedia(savedPost, (short) 1));
        entityManager.persistAndFlush(minimalMedia(savedPost, (short) 2));
        entityManager.flush();
        entityManager.clear();

        long count = entityManager.getEntityManager()
                .createQuery("SELECT COUNT(m) FROM PostMediaJpaEntity m WHERE m.post.id = :postId", Long.class)
                .setParameter("postId", savedPost.getId())
                .getSingleResult();

        assertThat(count).isEqualTo(3);
    }
}
