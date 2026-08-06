package com.instagram.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.Setter;
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

import com.instagram.adapter.out.persistence.entity.BaseJpaEntity;

import static org.assertj.core.api.Assertions.assertThat;

// This test persists an ad-hoc DummyEntity that Flyway migrations know nothing about,
// so it can't reuse the shared PostgresIntegrationTest (ddl-auto=none) — it needs its
// own container with Hibernate schema auto-generation (create-drop) instead.
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(com.instagram.infrastructure.config.JpaConfig.class)
class BaseJpaEntityTest {

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
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private TestEntityManager entityManager;

    @Entity
    @Getter
    @Setter
    public static class DummyEntity extends BaseJpaEntity {
        @Id
        @GeneratedValue
        private Long id;
        private String name;
    }

    @Test
    void shouldPopulateAuditFieldsOnSave() {
        // Arrange
        DummyEntity entity = new DummyEntity();
        entity.setName("test");

        // Act
        DummyEntity savedEntity = entityManager.persistFlushFind(entity);

        // Assert
        assertThat(savedEntity.getCreatedAt()).isNotNull();
        assertThat(savedEntity.getUpdatedAt()).isNotNull();
        assertThat(savedEntity.getId()).isNotNull();
    }

    @Test
    void shouldUpdateUpdatedAtFieldOnModification() throws InterruptedException {
        // Arrange
        DummyEntity entity = new DummyEntity();
        entity.setName("test");

        DummyEntity savedEntity = entityManager.persistFlushFind(entity);
        var initialUpdatedAt = savedEntity.getUpdatedAt();

        // Act
        Thread.sleep(10);
        savedEntity.setName("updated");
        DummyEntity updatedEntity = entityManager.persistFlushFind(savedEntity);

        // Assert
        assertThat(updatedEntity.getUpdatedAt()).isAfter(initialUpdatedAt);
    }
}
