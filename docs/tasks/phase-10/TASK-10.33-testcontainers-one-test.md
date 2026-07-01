# TASK-10.33 — Swap one integration test to Testcontainers

## Overview

The existing `SearchJpaAdapterIT` connects to a local `instagram_test` PostgreSQL database that developers must provision manually. This task replaces that hard-coded connection string with a Testcontainers `PostgreSQLContainer` that spins up an isolated, real Postgres database automatically in Docker, runs the test, and tears it down. No manual database setup is required after this change.

This is a warm-up exercise: it touches exactly one test file and introduces the Testcontainers API in a safe, controlled way before [TASK-10.39](TASK-10.39-testcontainers-integration-suite.md) applies the pattern to every `*IT` test.

---

## Level

Warm-up · Pairs with [TASK-10.35](TASK-10.35-coverage-gate.md) / [TASK-10.39](TASK-10.39-testcontainers-integration-suite.md)

---

## Why

H2 is an in-memory database that mimics SQL, but it does not behave identically to PostgreSQL. Features like `ILIKE`, full-text search (`tsvector`/`ts_rank`), the `citext` extension, and `uuid_generate_v4()` either behave differently or are simply absent in H2. A test can pass perfectly against H2 and then break in production where the real Postgres SQL is running. Testcontainers fixes this by running an actual Postgres Docker image during the test — the same engine your application uses in production. After completing this task you will have seen the Testcontainers API in action and be ready to scale it up in TASK-10.39.

---

## Prerequisites

- [TASK-10.39](TASK-10.39-testcontainers-integration-suite.md) is the full suite migration; complete this warm-up first.
- **Docker Desktop** (or Docker Engine) must be running on your machine. Testcontainers uses the Docker socket. Verify with:
  ```powershell
  docker info
  ```
- The `testcontainers` BOM is already imported transitively via Spring Boot 3.3.4; the `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter` artifacts are already declared in `backend/pom.xml` at test scope — no new dependency is needed.
- Concepts to skim:
  - **`@Testcontainers` / `@Container`** — JUnit 5 extension that manages container lifecycle; `@Container static` means the container is shared across all tests in the class. See [Testcontainers JUnit 5 docs](https://java.testcontainers.org/test_framework_integration/junit_5/).
  - **`@DynamicPropertySource`** — Spring Boot hook that lets you inject the container's dynamic JDBC URL into the Spring `Environment` before the `ApplicationContext` starts. See [Spring docs](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/test/context/DynamicPropertySource.html).
  - **`PostgreSQLContainer`** — wraps the official `postgres` Docker image and exposes `getJdbcUrl()`, `getUsername()`, `getPassword()`.

---

## Files to Create / Modify

```
backend/src/test/java/com/instagram/adapter/out/persistence/SearchJpaAdapterIT.java   (modify)
```

---

## Step-by-Step

### 1. Confirm the Docker daemon is running

Open a PowerShell terminal and run:

```powershell
docker info
```

You should see server information including the Docker version. If you get an error, start Docker Desktop before continuing.

---

### 2. Confirm the Testcontainers dependencies are already present

Open `backend/pom.xml` and search for `testcontainers`. You should already see both artifacts declared at test scope:

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

If either is missing, add it inside the `<dependencies>` block. The version is managed by Spring Boot's BOM (`spring-boot-dependencies` 3.3.4 ships Testcontainers 1.19.x).

---

### 3. Understand what the current test does

Open `backend/src/test/java/com/instagram/adapter/out/persistence/SearchJpaAdapterIT.java`.

The class is currently annotated with:

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=true",
        "spring.jpa.hibernate.ddl-auto=none",
        "spring.datasource.url=jdbc:postgresql://localhost:5432/instagram_test",
        "spring.datasource.username=instagram",
        "spring.datasource.password=changeme",
        "spring.datasource.driver-class-name=org.postgresql.Driver"
})
```

The four `datasource.*` properties point to a developer's local machine. Anyone without that exact database will see a `Connection refused` failure. The goal is to replace those hard-coded values with dynamic values supplied by a Testcontainers container.

---

### 4. Add the Testcontainers annotations and container field

At the top of `SearchJpaAdapterIT`, add the `@Testcontainers` class annotation and declare a `static` container field. Using `static` means the container starts once for the entire test class rather than restarting between each test method, which is faster.

Replace the class-level annotation block with:

```java
@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class SearchJpaAdapterIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("instagram_test")
                    .withUsername("instagram")
                    .withPassword("changeme");
```

The new imports needed at the top of the file:

```java
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
```

---

### 5. Replace the hard-coded `@TestPropertySource` with `@DynamicPropertySource`

Remove the `@TestPropertySource` annotation entirely from the class level.

Add a static method inside the class annotated with `@DynamicPropertySource`. Spring Boot calls this method before starting the `ApplicationContext`, so the container is already running and its port is known:

```java
@DynamicPropertySource
static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    registry.add("spring.flyway.enabled", () -> "true");
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
}
```

Add the import:

```java
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
```

---

### 6. Verify the full updated class header looks like this

```java
package com.instagram.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
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

// ... other imports unchanged ...

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
class SearchJpaAdapterIT {

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

    // ... rest of class unchanged ...
}
```

No changes to the test methods themselves are needed — they run against the Testcontainers Postgres exactly as written.

---

### 7. Run the test

From the `backend/` directory:

```powershell
cd C:\workspace\Instagram-Social\backend
mvn test -pl . -Dtest=SearchJpaAdapterIT -Dit.test=SearchJpaAdapterIT
```

Or run just the integration test class directly:

```powershell
mvn -pl . verify -Dtest=SearchJpaAdapterIT
```

**Expected output** (abbreviated):

```
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

You will see a line like `Pulling image 'postgres:15-alpine'` on the first run as Docker fetches the image. Subsequent runs use the cached image and start in seconds.

---

## Checklist

- [x] Add the Testcontainers + Postgres dependencies (test scope)
  - In this project they are already present — confirm by checking `backend/pom.xml` for `org.testcontainers:postgresql` and `org.testcontainers:junit-jupiter`
- [x] Convert `SearchJpaAdapterIT` to use a `@Container PostgreSQLContainer`
  - [x] Add `@Testcontainers` class annotation
  - [x] Declare `static final PostgreSQLContainer<?> POSTGRES` with `@Container`
  - [x] Replace `@TestPropertySource` with a `@DynamicPropertySource` static method
- [x] Confirm the test passes against real Postgres, not H2
  - [x] `mvn test -Dtest=SearchJpaAdapterIT` exits with `BUILD SUCCESS`
  - [x] Full-text search tests (`searchPosts_ftsStemming`, `searchPosts_ftsMultiWord`, `searchPosts_ftsRelevanceOrdering`) pass — these require real Postgres and would fail or be meaningless on H2

---

## How to Verify

Run the single test class:

```powershell
cd C:\workspace\Instagram-Social\backend
mvn test "-Dtest=SearchJpaAdapterIT"
```

Passing result:

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.instagram.adapter.out.persistence.SearchJpaAdapterIT
...
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 12.345 s
[INFO]
[INFO] BUILD SUCCESS
```

The first run will be slower (Docker image pull). Confirm that the FTS-specific tests (`searchPosts_ftsStemming`, `searchPosts_ftsRelevanceOrdering`) are among those that pass — they exercise Postgres-only functionality and are the whole point of this migration.

---

## Notes / Gotchas

- **"Cannot connect to Docker"** — Testcontainers looks for the Docker socket (`/var/run/docker.sock` on Linux/macOS, named pipe on Windows). If Docker Desktop is not running you will see `Could not find a valid Docker environment`. Start Docker Desktop first.

- **Static vs. instance `@Container`** — A `static` field means one container shared across all test methods in the class (faster). An instance field means a new container per test (more isolation, much slower). Use `static` here.

- **Port collision** — Testcontainers maps the container to a random ephemeral host port, so it will never conflict with your local Postgres running on `5432`.

- **`@TestPropertySource` vs `@DynamicPropertySource`** — `@TestPropertySource` values are fixed at compile time, so you cannot use them with a dynamically assigned port. Always use `@DynamicPropertySource` with Testcontainers.

- **Flyway and extensions** — The container starts with a blank Postgres. Flyway applies `V1__initial_schema.sql`, `V2__seed_data.sql`, and `V3__add_fts_indexes.sql` automatically because `spring.flyway.enabled=true` is set in the dynamic properties. Extensions like `pg_trgm` and `citext` are created by the migration, so no extra setup is needed. If a migration fails you will see `FlywayException: Validate failed` in the output — check the SQL file for Postgres-specific syntax that H2 may have been silently accepting.

- **Slow first run** — The `postgres:15-alpine` image is ~80 MB. The first `docker pull` adds 30–60 seconds. Use `alpine` variants to keep images small.

- Related tasks: [TASK-10.39](TASK-10.39-testcontainers-integration-suite.md) applies this pattern to every `*IT` class in the project.

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **What Testcontainers is** — spin up real services (Postgres) in Docker for tests — https://testcontainers.com/getting-started/
- **Real DB vs H2/mocks** — why tests against the real engine catch more bugs — https://www.baeldung.com/spring-boot-testcontainers-integration-test
- **PostgreSQL module** — the container that backs your integration test — https://java.testcontainers.org/modules/databases/postgres/

### Official docs (code reference)
- **Testcontainers for Java** — https://java.testcontainers.org/
- **Testcontainers getting started** — https://testcontainers.com/getting-started/
