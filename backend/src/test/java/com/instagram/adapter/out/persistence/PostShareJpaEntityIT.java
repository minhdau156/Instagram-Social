package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.entity.PostShareJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.UserStatus;

class PostShareJpaEntityIT extends PostgresIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    private PostShareJpaEntity buildShare(UUID postId, UUID sharerId, UUID recipientId) {
        return PostShareJpaEntity.builder()
                .id(UUID.randomUUID())
                .postId(postId)
                .sharerId(sharerId)
                .recipientId(recipientId)
                .build();
    }

    // post_shares.post_id / shared_by_id / shared_to_id are real FKs under
    // Postgres — every share needs actually-persisted parent rows rather
    // than bare UUID.randomUUID() values.
    private UUID persistUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return entityManager.persistAndFlush(UserJpaEntity.builder()
                .username("share_user_" + suffix)
                .email("share_user_" + suffix + "@example.com")
                .fullName("Share User")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build()).getId();
    }

    private UUID persistPost(UUID userId) {
        return entityManager.persistAndFlush(PostJpaEntity.builder()
                .userId(userId)
                .status(PostStatus.PUBLISHED)
                .build()).getId();
    }

    @Test
    void shouldPersistDmShareWithRecipient() {
        UUID sharerId = persistUser();
        UUID recipientId = persistUser();
        UUID postId = persistPost(sharerId);

        PostShareJpaEntity saved = entityManager.persistFlushFind(buildShare(postId, sharerId, recipientId));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPostId()).isEqualTo(postId);
        assertThat(saved.getSharerId()).isEqualTo(sharerId);
        assertThat(saved.getRecipientId()).isEqualTo(recipientId);
    }

    @Test
    void shouldPersistLinkShareWithNullRecipient() {
        UUID sharerId = persistUser();
        UUID postId = persistPost(sharerId);

        PostShareJpaEntity saved = entityManager.persistFlushFind(buildShare(postId, sharerId, null));

        assertThat(saved.getRecipientId()).isNull();
    }

    @Test
    void shouldAutoPopulateCreatedAt() {
        UUID sharerId = persistUser();
        UUID postId = persistPost(sharerId);

        PostShareJpaEntity saved = entityManager.persistFlushFind(
                buildShare(postId, sharerId, null));

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldUseProvidedIdAsPrimaryKey() {
        UUID id = UUID.randomUUID();
        UUID sharerId = persistUser();
        UUID postId = persistPost(sharerId);
        PostShareJpaEntity entity = PostShareJpaEntity.builder()
                .id(id)
                .postId(postId)
                .sharerId(sharerId)
                .build();

        entityManager.persistAndFlush(entity);
        entityManager.clear();

        PostShareJpaEntity found = entityManager.find(PostShareJpaEntity.class, id);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(id);
    }

    @Test
    void shouldPersistMultipleSharesForOnePost() {
        UUID ownerId = persistUser();
        UUID postId = persistPost(ownerId);
        UUID sharer1 = persistUser();
        UUID recipient1 = persistUser();
        UUID sharer2 = persistUser();

        entityManager.persistAndFlush(buildShare(postId, sharer1, recipient1));
        entityManager.persistAndFlush(buildShare(postId, sharer2, null));
        entityManager.flush();
        entityManager.clear();

        long count = entityManager.getEntityManager()
                .createQuery("SELECT COUNT(s) FROM PostShareJpaEntity s WHERE s.postId = :postId", Long.class)
                .setParameter("postId", postId)
                .getSingleResult();

        assertThat(count).isEqualTo(2);
    }
}
