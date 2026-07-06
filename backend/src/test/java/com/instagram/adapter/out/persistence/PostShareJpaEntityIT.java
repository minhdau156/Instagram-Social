package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.instagram.adapter.out.persistence.entity.PostShareJpaEntity;
import com.instagram.infrastructure.config.JpaConfig;

@DataJpaTest
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class PostShareJpaEntityIT {

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

    @Test
    void shouldPersistDmShareWithRecipient() {
        UUID postId = UUID.randomUUID();
        UUID sharerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        PostShareJpaEntity saved = entityManager.persistFlushFind(buildShare(postId, sharerId, recipientId));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getPostId()).isEqualTo(postId);
        assertThat(saved.getSharerId()).isEqualTo(sharerId);
        assertThat(saved.getRecipientId()).isEqualTo(recipientId);
    }

    @Test
    void shouldPersistLinkShareWithNullRecipient() {
        UUID postId = UUID.randomUUID();
        UUID sharerId = UUID.randomUUID();

        PostShareJpaEntity saved = entityManager.persistFlushFind(buildShare(postId, sharerId, null));

        assertThat(saved.getRecipientId()).isNull();
    }

    @Test
    void shouldAutoPopulateCreatedAt() {
        PostShareJpaEntity saved = entityManager.persistFlushFind(
                buildShare(UUID.randomUUID(), UUID.randomUUID(), null));

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldUseProvidedIdAsPrimaryKey() {
        UUID id = UUID.randomUUID();
        PostShareJpaEntity entity = PostShareJpaEntity.builder()
                .id(id)
                .postId(UUID.randomUUID())
                .sharerId(UUID.randomUUID())
                .build();

        entityManager.persistAndFlush(entity);
        entityManager.clear();

        PostShareJpaEntity found = entityManager.find(PostShareJpaEntity.class, id);
        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(id);
    }

    @Test
    void shouldPersistMultipleSharesForOnePost() {
        UUID postId = UUID.randomUUID();
        entityManager.persistAndFlush(buildShare(postId, UUID.randomUUID(), UUID.randomUUID()));
        entityManager.persistAndFlush(buildShare(postId, UUID.randomUUID(), null));
        entityManager.flush();
        entityManager.clear();

        long count = entityManager.getEntityManager()
                .createQuery("SELECT COUNT(s) FROM PostShareJpaEntity s WHERE s.postId = :postId", Long.class)
                .setParameter("postId", postId)
                .getSingleResult();

        assertThat(count).isEqualTo(2);
    }
}
