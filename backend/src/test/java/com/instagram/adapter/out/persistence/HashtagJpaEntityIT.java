package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.instagram.adapter.out.persistence.entity.HashtagJpaEntity;

import jakarta.persistence.PersistenceException;

class HashtagJpaEntityIT extends PostgresIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    private HashtagJpaEntity buildHashtag(String name, int postCount) {
        return HashtagJpaEntity.builder()
                .name(name)
                .postCount(postCount)
                .build();
    }

    @Test
    void shouldPersistAndRetrieveHashtag() {
        HashtagJpaEntity saved = entityManager.persistFlushFind(buildHashtag("spring", 42));

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("spring");
        assertThat(saved.getPostCount()).isEqualTo(42);
    }

    @Test
    void shouldAutoPopulateCreatedAt() {
        HashtagJpaEntity saved = entityManager.persistFlushFind(buildHashtag("java", 10));

        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void shouldGenerateUniqueId() {
        HashtagJpaEntity h1 = entityManager.persistFlushFind(buildHashtag("kotlin", 5));
        HashtagJpaEntity h2 = entityManager.persistFlushFind(buildHashtag("scala", 3));

        assertThat(h1.getId()).isNotEqualTo(h2.getId());
    }

    @Test
    void shouldPersistZeroPostCount() {
        HashtagJpaEntity saved = entityManager.persistFlushFind(buildHashtag("newhashtag", 0));

        assertThat(saved.getPostCount()).isZero();
    }

    @Test
    void shouldRejectDuplicateHashtagName() {
        entityManager.persistAndFlush(buildHashtag("duplicate", 1));

        assertThrows(PersistenceException.class, () ->
                entityManager.persistAndFlush(buildHashtag("duplicate", 2)));
    }
}
