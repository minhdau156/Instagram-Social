package com.instagram.adapter.out.persistence;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.instagram.infrastructure.config.JpaConfig;

/**
 * Base class for all @DataJpaTest integration tests.
 *
 * <p>Starts a single shared PostgreSQL container for the entire test suite
 * (singleton pattern). The container is started once when the first subclass
 * loads, reused by every subsequent subclass, and stopped when the JVM exits.
 *
 * <p>Flyway applies all migrations on first startup, so pg_trgm, citext, and
 * FTS indexes are available in every test.
 */
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
public abstract class PostgresIntegrationTest {

    // Static = one container instance shared across ALL subclasses in the JVM.
    // Testcontainers keeps it alive until the JVM shuts down (Ryuk cleans it up).
    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES = new PostgreSQLContainer<>("postgres:15-alpine")
                .withDatabaseName("instagram_test")
                .withUsername("instagram")
                .withPassword("changeme")
                // Reuse the container across test classes for speed.
                // Requires ~/.testcontainers.properties to contain
                // testcontainers.reuse.enable=true (optional but recommended locally)
                .withReuse(true);
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }
}
