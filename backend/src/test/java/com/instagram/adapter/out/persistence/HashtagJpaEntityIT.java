package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.instagram.adapter.out.persistence.entity.HashtagJpaEntity;
import com.instagram.infrastructure.config.JpaConfig;

import jakarta.persistence.PersistenceException;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class HashtagJpaEntityIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("instagram_test")
                    .withUsername("instagram")
                    .withPassword("changeme");

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

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
