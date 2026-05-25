# TASK-10.39 — Testcontainers for the integration suite

## Overview

Migrate all `*IT` persistence and integration tests from H2 (or a hard-coded local Postgres connection) to a shared, singleton Testcontainers `PostgreSQLContainer`. Create a base class that starts one Postgres Docker container for the entire test suite, applies Flyway migrations (including `pg_trgm`, `citext`, and FTS indexes), and provides a `@DynamicPropertySource` method that injects the container's JDBC URL into every subclass. Once the base class is in place, update each `*IT` file to extend it, remove H2 configuration, and verify the full test suite passes against real Postgres in CI.

This is the full migration that [TASK-10.33](TASK-10.33-testcontainers-one-test.md) prototypes on a single test class.

---

## Level

Core · Pairs with [TASK-10.33](TASK-10.33-testcontainers-one-test.md) (warm-up on a single test) and [TASK-10.35](TASK-10.35-coverage-gate.md) (coverage gate)

---

## Why

H2 silently diverges from Postgres in ways that only appear in production. The `ILIKE` operator, full-text search (`tsvector`, `ts_rank`), the `citext` extension for case-insensitive columns, `uuid_generate_v4()`, partial indexes, and PostgreSQL-specific constraint names either behave differently in H2 or do not exist at all. A test suite that passes on H2 can mask bugs that only surface when the real Postgres engine runs the same SQL. Testcontainers solves this definitively: it runs the same `postgres:15` Docker image that production uses, applies the project's Flyway migrations, and tears the container down after the suite finishes. The result is a test suite whose green status actually means something.

---

## Prerequisites

- Complete [TASK-10.33](TASK-10.33-testcontainers-one-test.md) first — it walks through the Testcontainers API on a single file so you understand what you are scaling up here.
- **Docker Desktop must be running**: `docker info` must succeed before running the tests.
- The Testcontainers Maven artifacts are already declared in `backend/pom.xml` at test scope (`org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter`) — no new dependencies needed.
- Concepts to skim:
  - **Singleton container pattern** — a single `static` `PostgreSQLContainer` field declared in a shared base class, reused by all subclasses. See [Testcontainers — Singleton containers](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/#singleton-containers).
  - **`@DynamicPropertySource`** — Spring Boot hook for injecting dynamic configuration (like a container's port) into the `Environment` before the `ApplicationContext` starts. See [Spring docs](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/test/context/DynamicPropertySource.html).
  - **Flyway in tests** — setting `spring.flyway.enabled=true` in the dynamic properties causes Spring Boot's Flyway auto-configuration to run all migrations (`V1__initial_schema.sql`, `V2__seed_data.sql`, `V3__add_fts_indexes.sql`) against the test container on startup.
  - **`pg_trgm` and `citext`** — PostgreSQL extensions installed by `V1__initial_schema.sql`. They are present in the standard `postgres:15` image and activated by the migration. H2 has no equivalent.

---

## Files to Create / Modify

```
backend/pom.xml                                                                                                (modify — confirm Testcontainers deps; remove H2 test scope if present)
backend/src/test/java/com/instagram/adapter/out/persistence/PostgresIntegrationTest.java                      (new — shared base class)
backend/src/test/java/com/instagram/adapter/out/persistence/SearchJpaAdapterIT.java                           (modify — extend base class, remove @TestPropertySource)
backend/src/test/java/com/instagram/adapter/out/persistence/UserPersistenceAdapterIT.java                     (modify)
backend/src/test/java/com/instagram/adapter/out/persistence/PostPersistenceAdapterIT.java                     (modify)
backend/src/test/java/com/instagram/adapter/out/persistence/CommentPersistenceAdapterIT.java                  (modify)
backend/src/test/java/com/instagram/adapter/out/persistence/LikePersistenceAdapterIT.java                     (modify)
backend/src/test/java/com/instagram/adapter/out/persistence/SavedPostPersistenceAdapterIT.java                (modify)
backend/src/test/java/com/instagram/adapter/out/persistence/FollowPersistenceAdapterIT.java                   (modify)
backend/src/test/java/com/instagram/adapter/out/persistence/ConversationPersistenceAdapterIT.java             (modify)
backend/src/test/java/com/instagram/adapter/out/persistence/NotificationPersistenceAdapterIT.java             (modify)
backend/src/test/java/com/instagram/adapter/out/persistence/FeedJpaQueryAdapterIT.java                        (modify)
backend/src/test/java/com/instagram/adapter/out/persistence/UserJpaEntityIT.java                              (modify)
.github/workflows/ci.yml                                                                                       (verify — Docker is available; no change needed since ubuntu-latest has Docker)
```

---

## Step-by-Step

### 1. Confirm the Testcontainers dependencies are present

Open `backend/pom.xml` and verify both artifacts exist at test scope:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

Also check whether H2 is present. If it is, you may leave it for now and remove it in step 7 once all tests have migrated.

---

### 2. Create the shared base class

Create `backend/src/test/java/com/instagram/adapter/out/persistence/PostgresIntegrationTest.java`:

```java
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
 * <p>Flyway applies all migrations (V1–V3) on first startup, so pg_trgm,
 * citext, and FTS indexes are available in every test.
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
                // Requires the file ~/.testcontainers.properties to contain
                // testcontainers.reuse.enable=true  (optional but recommended locally)
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
```

Key design choices:
- The container is declared in a `static {}` block so it starts before any test runs, even before `@BeforeAll`.
- `withReuse(true)` allows Testcontainers to keep the container alive between test class runs in the same JVM process (helpful for IDE incremental test runs). It has no effect in CI where each run is a fresh JVM.
- The `@DynamicPropertySource` is inherited by all subclasses automatically.

---

### 3. Enable container reuse locally (optional but recommended)

On your development machine, create or edit `~/.testcontainers.properties`:

```
testcontainers.reuse.enable=true
```

On Windows the home directory is `C:\Users\<your-username>`. Use:

```powershell
Add-Content -Path "$env:USERPROFILE\.testcontainers.properties" -Value "testcontainers.reuse.enable=true"
```

This keeps the container alive between test runs so the 2–3 second startup cost only happens once per dev session.

---

### 4. Migrate `SearchJpaAdapterIT` (already done in TASK-10.33)

If you completed [TASK-10.33](TASK-10.33-testcontainers-one-test.md), `SearchJpaAdapterIT` already uses `@DynamicPropertySource`. Simplify it now by extending the base class instead of re-declaring the container:

```java
@DataJpaTest          // ← already on the class; remove it here since base class declares it
// @Testcontainers    // ← remove — inherited from base
// @AutoConfigureTestDatabase(...)  // ← remove — inherited from base
// @Import(JpaConfig.class)         // ← remove — inherited from base
class SearchJpaAdapterIT extends PostgresIntegrationTest {

    // Remove the @Container POSTGRES field and @DynamicPropertySource method —
    // both are inherited from PostgresIntegrationTest.

    // @BeforeEach, @Test methods, and helper methods remain unchanged.
}
```

Because `@DataJpaTest` is already on the base class, you can remove it from the subclass too (it would be redundant). However, if your IDE or Maven reports a conflict, keep it on the subclass — Spring Boot merges duplicate `@DataJpaTest` annotations safely.

---

### 5. Migrate each remaining `*IT` class

For every other IT class in `adapter/out/persistence/`, apply the same change:

1. Remove `@TestPropertySource` if present.
2. Remove any inline `@Container` field and `@DynamicPropertySource` method if the class had its own.
3. Add `extends PostgresIntegrationTest`.
4. Remove redundant class-level annotations that are now inherited (`@DataJpaTest`, `@Testcontainers`, `@AutoConfigureTestDatabase`, `@Import(JpaConfig.class)`).

Example for `UserPersistenceAdapterIT`:

```java
// Before
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL",
    ...
})
class UserPersistenceAdapterIT { ... }

// After
class UserPersistenceAdapterIT extends PostgresIntegrationTest { ... }
```

The `@BeforeEach` truncation query (if any) continues to work because it uses `EntityManager.createNativeQuery(...)` which now targets the real Postgres container.

---

### 6. Verify Postgres-specific SQL works in the tests

After migration, run the full persistence IT suite:

```powershell
cd C:\workspace\Instagram-Social\backend
mvn test "-Dtest=Search*IT,User*IT,Post*IT,Comment*IT,Like*IT,SavedPost*IT,Follow*IT,Conversation*IT,Notification*IT,Feed*IT"
```

Watch for failures that indicate H2-to-Postgres SQL incompatibilities. Common ones:

| H2 pattern | Postgres equivalent | Fix |
|---|---|---|
| `H2 MODE=PostgreSQL` native ILIKE | Real `ILIKE` | Works — Postgres natively supports `ILIKE` |
| `VALUE()` or `SEQUENCE.NEXTVAL` | `uuid_generate_v4()` | Handled by Flyway migration |
| Unnamed constraint violations | Named constraint (`uq_users_username`) | Catch the specific `DataIntegrityViolationException` and assert on message |
| `citext` comparison | Real case-insensitive match | Works with V1 migration which installs `citext` extension |

---

### 7. Remove the H2 test dependency once all tests pass

Once every `*IT` class extends `PostgresIntegrationTest` and the full suite passes, remove H2 from `backend/pom.xml`:

```xml
<!-- Remove this entire block -->
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

Run `mvn verify` to confirm nothing depends on H2 any more. If the build fails with a `ClassNotFoundException` for H2, a test file still has an H2 reference — search for `h2` in the test directory:

```powershell
Select-String -Path "C:\workspace\Instagram-Social\backend\src\test\**\*.java" -Pattern "h2" -Recurse
```

---

### 8. Verify CI has Docker available

The GitHub Actions `ubuntu-latest` runner has Docker pre-installed — Testcontainers works out of the box. No changes to `.github/workflows/ci.yml` are needed.

To confirm, check that the existing `backend-ci` job in the workflow does not pass `--no-docker` or any other flag that would disable Testcontainers. The existing step is:

```yaml
- name: Build and verify backend
  working-directory: ./backend
  run: mvn verify
```

This is correct. Testcontainers will connect to the Docker daemon provided by the runner.

> Note: The CI workflow already has a `services.postgres` block that starts a Postgres container for `SearchJpaAdapterIT`'s old hard-coded connection. After this migration, that `services.postgres` block is no longer needed (Testcontainers starts its own). You may remove it from the `backend-ci` job to simplify the workflow, but leaving it does no harm — it will simply be an unused service.

---

### 9. Run the full verification

```powershell
cd C:\workspace\Instagram-Social\backend
mvn verify
```

This runs compile → unit tests → integration tests → JaCoCo report → coverage gate. All `*IT` tests run against the Testcontainers Postgres.

---

## Checklist

- [ ] Add Testcontainers (`postgresql`, `junit-jupiter`) at test scope
  - [ ] Confirm both artifacts are present in `backend/pom.xml` (they are already there)
- [ ] Create a shared `@Container PostgreSQLContainer` base class (singleton/reused container) with Flyway migrations applied
  - [ ] `PostgresIntegrationTest.java` created in `adapter/out/persistence/`
  - [ ] Container declared in a `static {}` block so it starts once for all subclasses
  - [ ] `@DynamicPropertySource` sets all four datasource properties and enables Flyway
  - [ ] Class annotated with `@DataJpaTest`, `@Testcontainers`, `@AutoConfigureTestDatabase(replace=NONE)`, `@Import(JpaConfig.class)`
- [ ] Migrate the `@DataJpaTest` ITs off H2 onto the container; enable `pg_trgm` / `citext` so FTS and case-insensitive tests are real
  - [ ] `SearchJpaAdapterIT` extended from `PostgresIntegrationTest`; no inline container or `@TestPropertySource`
  - [ ] `UserPersistenceAdapterIT` migrated
  - [ ] `PostPersistenceAdapterIT` migrated
  - [ ] `CommentPersistenceAdapterIT` migrated
  - [ ] `LikePersistenceAdapterIT` migrated
  - [ ] `SavedPostPersistenceAdapterIT` migrated
  - [ ] `FollowPersistenceAdapterIT` migrated
  - [ ] `ConversationPersistenceAdapterIT` migrated
  - [ ] `NotificationPersistenceAdapterIT` migrated
  - [ ] `FeedJpaQueryAdapterIT` migrated
  - [ ] `UserJpaEntityIT` migrated
- [ ] Ensure CI provides Docker for the Testcontainers run
  - [ ] `ubuntu-latest` runner on GitHub Actions has Docker pre-installed — no extra setup needed
  - [ ] `mvn verify` in the `backend-ci` CI job runs all migrated ITs successfully
- [ ] Remove the H2 test dependency/config once nothing depends on it
  - [ ] H2 artifact removed from `pom.xml`
  - [ ] `mvn verify` still passes after removal
  - [ ] No `h2` references remain in test sources

---

## How to Verify

Run the complete backend test suite:

```powershell
cd C:\workspace\Instagram-Social\backend
mvn verify
```

Passing result (abbreviated):

```
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0  -- SearchJpaAdapterIT
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0  -- UserPersistenceAdapterIT
[INFO] Tests run: 9,  Failures: 0, Errors: 0, Skipped: 0  -- PostPersistenceAdapterIT
...
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

To isolate just the persistence IT classes:

```powershell
mvn test "-Dtest=*IT"
```

The first run will take 30–60 seconds to pull the `postgres:15-alpine` image and start the container. Subsequent runs on the same machine reuse the cached image and start in 2–4 seconds (with `.withReuse(true)` enabled).

Confirm that the FTS-specific tests in `SearchJpaAdapterIT` pass — `searchPosts_ftsStemming`, `searchPosts_ftsMultiWord`, and `searchPosts_ftsRelevanceOrdering` exercise Postgres-only full-text search and would have been meaningless on H2.

---

## Notes / Gotchas

- **Singleton container vs. per-class container** — The singleton pattern (one `static` field in a base class shared across all subclasses) is the fastest approach. It means the Postgres database is shared between test classes, so test isolation depends on each `@BeforeEach` truncating its tables. If a test class does not truncate, leftover data from a previous class can cause unexpected failures. Always include a truncation step in `@BeforeEach`.

- **`@DataJpaTest` vs `@SpringBootTest`** — `@DataJpaTest` loads only the JPA slice (entities, repositories, `EntityManager`). It does not start the full HTTP server. Use it for persistence adapter tests. Controller tests use `@WebMvcTest` and do not need Testcontainers (they mock the service layer).

- **`withReuse(true)` requires the `testcontainers.reuse.enable=true` property** — Without the property file, `withReuse(true)` has no effect and the container is recreated each time. This is fine in CI (containers are fast to start on `ubuntu-latest`) but slow locally during iterative development.

- **GitHub Actions Docker socket** — `ubuntu-latest` runners have Docker installed and the socket accessible at `/var/run/docker.sock`. Testcontainers auto-detects this. No `DOCKER_HOST` or `TESTCONTAINERS_HOST_OVERRIDE` environment variable is needed.

- **The existing CI `services.postgres` block** — The `backend-ci` job currently starts a Postgres service for `SearchJpaAdapterIT`'s old hard-coded connection string. After migration, this service is unused. You can safely remove it to reduce job startup time.

- **H2 `MODE=PostgreSQL`** — Some earlier tests may use `jdbc:h2:mem:testdb;MODE=PostgreSQL` in a `@TestPropertySource`. This mode approximates Postgres syntax but does not support `ILIKE`, `citext`, `pg_trgm`, or native Postgres ENUMs. After migration these tests will be running against the real thing.

- **Flyway `baseline-on-migrate`** — If the container's database already has schema objects (from a previous test run that was not cleaned up), Flyway's validation may fail. The `POSTGRES.start()` in the `static {}` block creates a fresh database each time the JVM starts, so this is not an issue in normal usage. With `withReuse(true)`, the database persists between runs — if a migration is modified and the container is reused, you may need to stop and restart the container manually.

- Official docs: [Testcontainers — JUnit 5 integration](https://java.testcontainers.org/test_framework_integration/junit_5/), [Testcontainers — Singleton containers](https://java.testcontainers.org/test_framework_integration/manual_lifecycle_control/#singleton-containers), [Testcontainers — PostgreSQL module](https://java.testcontainers.org/modules/databases/postgres/).

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Container lifecycle in JUnit 5** — start once per suite vs per test — https://java.testcontainers.org/test_framework_integration/junit_5/
- **Sharing one container (singleton pattern)** — speed up the whole suite — https://java.testcontainers.org/
- **Spring Boot integration testing** — `@SpringBootTest` against a real DB — https://www.baeldung.com/spring-boot-testcontainers-integration-test

### Official docs (code reference)
- **Testcontainers for Java** — https://java.testcontainers.org/
- **PostgreSQL module** — https://java.testcontainers.org/modules/databases/postgres/
