package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.instagram.adapter.out.persistence.repository.HashtagJpaRepository;
import com.instagram.domain.model.Hashtag;
import com.instagram.infrastructure.config.JpaConfig;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
public class HashtagPersistenceAdapterIT {

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
    private HashtagJpaRepository hashtagJpaRepository;

    private HashtagPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new HashtagPersistenceAdapter(hashtagJpaRepository);
    }

    @Test
    void save_persistsHashtagWithCorrectFields() {
        // given
        Hashtag hashtag = Hashtag.builder()
                .name("spring")
                .postCount(5)
                .build();

        // when
        Hashtag saved = adapter.save(hashtag);

        // then
        assertNotNull(saved.getId());
        assertEquals("spring", saved.getName());
        assertEquals(5, saved.getPostCount());
    }

    @Test
    void findByName_whenExists_returnsHashtag() {
        // given
        adapter.save(Hashtag.builder().name("java").postCount(10).build());

        // when
        Optional<Hashtag> result = adapter.findByName("java");

        // then
        assertTrue(result.isPresent());
        assertEquals("java", result.get().getName());
        assertEquals(10, result.get().getPostCount());
    }

    @Test
    void findByName_whenNotExists_returnsEmpty() {
        // given / when
        Optional<Hashtag> result = adapter.findByName("nonexistent");

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void findOrCreate_whenHashtagNotExists_createsNew() {
        // given / when
        Hashtag result = adapter.findOrCreate("newhashtag");

        // then
        assertNotNull(result.getId());
        assertEquals("newhashtag", result.getName());
        assertEquals(0, result.getPostCount());
        assertThat(adapter.findByName("newhashtag")).isPresent();
    }

    @Test
    void findOrCreate_whenHashtagExists_returnsExistingWithoutCreatingDuplicate() {
        // given
        Hashtag original = adapter.save(Hashtag.builder().name("existing").postCount(3).build());

        // when
        Hashtag result = adapter.findOrCreate("existing");

        // then
        assertEquals(original.getId(), result.getId());
        assertEquals(3, result.getPostCount());
        assertEquals(1, hashtagJpaRepository.count());
    }
}
