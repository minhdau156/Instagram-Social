# TASK-10.35 — Backend test coverage gate

## Overview

Add the JaCoCo Maven plugin to `backend/pom.xml` with a build-breaking threshold of 80% instruction coverage. Configure it so that `mvn verify` automatically fails if coverage drops below the threshold. Then audit the existing test suite for gaps in domain service and controller tests — particularly exception-handler branches — and write the tests needed to reach the target.

After this task, every pull request is verified by CI (`mvn verify` runs in `.github/workflows/ci.yml`), so coverage regressions are caught automatically before they merge.

---

## Level

Core · Pairs with [TASK-10.34](TASK-10.34-cover-untested-branch.md) (find your first gap) and [TASK-10.33](TASK-10.33-testcontainers-one-test.md) (run integration tests against real Postgres)

---

## Why

Without a coverage gate, the test suite can quietly drift toward zero. A developer adds a new service method with five exception paths, forgets to test three of them, and the CI pipeline stays green — because nothing checked coverage. An automated gate makes "did we test the error paths?" a factual, machine-verified answer instead of a hope. Setting the threshold at 80% is a balance: it rules out untested features while leaving room for generated code, configuration classes, and the occasional infrastructure adapter that is hard to unit-test. The JaCoCo plugin enforces this at build time, not at review time.

---

## Prerequisites

- [TASK-10.34](TASK-10.34-cover-untested-branch.md) — complete the warm-up first so you understand how to read the JaCoCo report before the gate rejects your build.
- The backend must build cleanly: `mvn compile` exits with `BUILD SUCCESS`.
- No new tooling required — JaCoCo is a Maven plugin that downloads automatically.
- Concepts to skim:
  - **JaCoCo** — a Java code coverage library that instruments bytecode and produces HTML, XML, and CSV reports. See [jacoco.org](https://www.jacoco.org/jacoco/trunk/doc/index.html).
  - **Instruction vs. branch coverage** — JaCoCo can gate on `INSTRUCTION`, `BRANCH`, `LINE`, `METHOD`, or `CLASS`. This task uses `INSTRUCTION` (the most common threshold type) at 80%.
  - **`mvn verify`** — runs compile → test → package → integration-test → verify phases in order. JaCoCo's `check` goal binds to the `verify` phase.
  - **`@ExceptionHandler` branches** — each handler method in `GlobalExceptionHandler` is a branch; covering it requires a test that actually throws the mapped domain exception through the HTTP layer.

---

## Files to Create / Modify

```
backend/pom.xml                                                                                (modify)
backend/src/test/java/com/instagram/adapter/in/web/PostControllerIT.java                      (modify — fill gaps)
backend/src/test/java/com/instagram/adapter/in/web/AuthControllerIT.java                      (modify — fill gaps)
backend/src/test/java/com/instagram/adapter/in/web/SearchControllerIT.java                    (modify — fill gaps)
backend/src/test/java/com/instagram/application/service/UserServiceTest.java                   (modify — fill gaps)
backend/src/test/java/com/instagram/application/service/PostServiceTest.java                   (modify — fill gaps)
```

You may need to modify additional test files depending on which gaps the JaCoCo report reveals. The list above is a starting point based on the areas most likely to be under-tested after phases 1–9.

---

## Step-by-Step

### 1. Add the JaCoCo Maven plugin to `pom.xml`

Open `backend/pom.xml`. Locate the `<build><plugins>` section (create it if absent). Add the following plugin block:

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <!-- Version is managed by spring-boot-dependencies BOM -->
    <executions>
        <!-- Instruments the JVM before tests run -->
        <execution>
            <id>prepare-agent</id>
            <goals>
                <goal>prepare-agent</goal>
            </goals>
        </execution>
        <!-- Generates HTML/XML report after tests -->
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals>
                <goal>report</goal>
            </goals>
        </execution>
        <!-- Fails the build if coverage is below threshold -->
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>INSTRUCTION</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
                <!-- Exclude infrastructure boilerplate from the gate -->
                <excludes>
                    <exclude>com/instagram/SocialMediaApplication.class</exclude>
                    <exclude>com/instagram/infrastructure/config/**</exclude>
                    <exclude>com/instagram/infrastructure/security/**</exclude>
                    <exclude>com/instagram/infrastructure/storage/**</exclude>
                </excludes>
            </configuration>
        </execution>
    </executions>
</plugin>
```

> The Spring Boot parent BOM manages the JaCoCo version (0.8.x). Do not pin a version explicitly unless you have a reason.

---

### 2. Run `mvn verify` and read the first failure

```powershell
cd C:\workspace\Instagram-Social\backend
mvn verify
```

If coverage is already above 80%, the build succeeds — skip to step 5 to verify. If it fails, you will see output like:

```
[ERROR] Rule violated for bundle social-media-backend:
        instructions covered ratio is 0.72, but expected minimum is 0.80
[INFO] BUILD FAILURE
```

This tells you the current coverage ratio. Note that number — the gap to close is `0.80 - current_ratio`.

---

### 3. Open the HTML report to identify gaps

```powershell
Start-Process "C:\workspace\Instagram-Social\backend\target\site\jacoco\index.html"
```

Navigate the package tree looking for red or yellow rows. Focus on these high-priority areas:

**Domain services** (`com.instagram.application.service`):
- Every service class should have a `*Test.java` at `backend/src/test/java/com/instagram/application/service/`. Check that exception paths (e.g., `NotFoundException` thrown when an entity is not found) have tests.
- `UserServiceTest`, `PostServiceTest`, `SearchServiceTest`, `NotificationServiceTest` all exist — click each in the JaCoCo report and look for red branches.

**Exception handler** (`com.instagram.adapter.in.web.GlobalExceptionHandler`):
- Each `@ExceptionHandler` method must be triggered by at least one test. If a handler's line is red, find the corresponding controller `*IT.java` and add a test that causes the service `MockBean` to throw the domain exception.

**Controllers** (`com.instagram.adapter.in.web`):
- Every endpoint must have at least a happy-path and a 401 test. Look for endpoints that return non-200 responses that are not yet tested.

---

### 4. Write tests to fill the gaps

For each uncovered branch, add one targeted test. Follow the patterns established in the existing test files.

**Pattern: covering a `GlobalExceptionHandler` branch in a `@WebMvcTest`:**

```java
// In PostControllerIT.java
@Test
void getPost_returns404_whenPostNotFound() throws Exception {
    UUID id = UUID.randomUUID();
    given(getPostUseCase.getPost(any())).willThrow(new PostNotFoundException(id));

    mockMvc.perform(get("/api/v1/posts/{id}", id)
                    .header("Authorization", "Bearer " + validToken))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").isNotEmpty());
}
```

**Pattern: covering a service guard in a unit test:**

```java
// In PostServiceTest.java
@Test
void deletePost_throwsUnauthorized_whenCallerIsNotOwner() {
    UUID postId = UUID.randomUUID();
    UUID ownerId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID();
    Post post = Post.builder().id(postId).userId(ownerId).build();
    given(postRepository.findById(postId)).willReturn(Optional.of(post));

    assertThatThrownBy(() ->
            postService.deletePost(new DeletePostUseCase.Command(postId, callerId))
    ).isInstanceOf(UnauthorizedPostAccessException.class);
}
```

Repeat for each red/yellow branch the report shows. Run `mvn verify` after each batch to check progress.

---

### 5. Confirm the gate passes

```powershell
cd C:\workspace\Instagram-Social\backend
mvn verify
```

Passing output (abbreviated):

```
[INFO] --- jacoco-maven-plugin:0.8.x:check (check) @ social-media-backend ---
[INFO] Loading execution data file .../target/jacoco.exec
[INFO] Analyzed bundle 'social-media-backend' with 87 classes
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

---

### 6. Verify CI enforces the gate

The GitHub Actions workflow at `.github/workflows/ci.yml` already runs `mvn verify` in the `backend-ci` job:

```yaml
- name: Build and verify backend
  working-directory: ./backend
  run: mvn verify
```

This means the coverage gate is automatically enforced on every push and pull request to `main`. No additional CI changes are needed.

To confirm locally that a deliberate coverage drop fails:

1. Temporarily raise the threshold in `pom.xml` to `0.99`.
2. Run `mvn verify` — it should fail.
3. Revert the threshold to `0.80`.

---

## Checklist

- [ ] Add `jacoco-maven-plugin` to `pom.xml`, fail build if coverage < 80%
  - [ ] `prepare-agent` execution present (instruments bytecode before test run)
  - [ ] `report` execution present (generates `target/site/jacoco/index.html`)
  - [ ] `check` execution present with `INSTRUCTION` counter `≥ 0.80`
  - [ ] Infrastructure config/security/storage packages excluded from the gate
- [ ] Identify and fill test gaps from phases 1–9 (prioritize domain services and controllers)
  - [ ] `UserServiceTest` — covers `UserNotFoundException`, `UserAlreadyExistsException`, `InvalidCredentialsException`
  - [ ] `PostServiceTest` — covers `PostNotFoundException`, `UnauthorizedPostAccessException`
  - [ ] `CommentServiceTest` — covers `CommentNotFoundException`, `UnauthorizedCommentAccessException`
  - [ ] `SearchServiceTest` — covers blank-query early-return branches
  - [ ] `NotificationServiceTest` — covers `NotificationNotFoundException`, `UnauthorizedNotificationAccessException`
  - [ ] `FollowServiceTest` — covers `AlreadyFollowingException`, `CannotFollowYourselfException`
- [ ] Ensure all exception handler branches are covered
  - [ ] Each `@ExceptionHandler` method in `GlobalExceptionHandler` is reached by at least one test
  - [ ] The test that triggers it asserts on the HTTP status code and the `error` field in the response JSON
- [ ] `mvn verify` exits with `BUILD SUCCESS` and the message "All coverage checks have been met"

---

## How to Verify

```powershell
cd C:\workspace\Instagram-Social\backend
mvn verify
```

Passing result:

```
[INFO] All coverage checks have been met.
[INFO] BUILD SUCCESS
```

Then open the HTML report and confirm the overall instruction coverage on the index page is 80% or above:

```powershell
Start-Process "C:\workspace\Instagram-Social\backend\target\site\jacoco\index.html"
```

The index page shows a summary row with `Instructions`, `Branches`, `Lines`, `Methods`, and `Classes` coverage. The `Instructions` column must show ≥ 80%.

To deliberately verify the gate rejects a drop in coverage, temporarily add `@ExcludeFromCoverage` or delete a test file, run `mvn verify`, and confirm `BUILD FAILURE` appears. Revert the change immediately.

---

## Notes / Gotchas

- **Spring Boot BOM manages the JaCoCo version** — do not add an explicit `<version>` tag to the plugin unless you have a specific reason. The version resolved by `spring-boot-starter-parent` 3.3.4 is recent and compatible.

- **`prepare-agent` must run before tests** — if you add only the `report` and `check` goals but omit `prepare-agent`, the `.exec` file that stores coverage data will not be created and coverage will report as 0%. The `prepare-agent` goal's default phase is `initialize`, which runs before `test`, so no explicit `<phase>` is needed.

- **`@SpringBootTest` vs `@WebMvcTest`** — `@WebMvcTest` loads only the web layer (controllers + `GlobalExceptionHandler`), making it faster for controller tests. To trigger a `GlobalExceptionHandler` branch, you must configure the relevant `@MockBean` to throw the domain exception. If you use `@SpringBootTest`, the full context loads and you can use a real `@Sql` script to set up data.

- **Infrastructure exclusions** — `SocialMediaApplication`, Spring config classes, and storage adapters are excluded because they are bootstrapping code with no meaningful unit tests. Excluding them prevents the gate from failing due to boilerplate. Do not exclude domain or application packages.

- **The CI pipeline already runs `mvn verify`** — no changes to `.github/workflows/ci.yml` are needed. The gate is automatically enforced from the moment you add the plugin.

- **Baseline reality** — After phases 1–9, instruction coverage is likely 60–75%. You will need to add 10–30 targeted tests. Focus on exception paths first (high value, low effort) before adding happy-path variations.

- Related tasks: [TASK-10.34](TASK-10.34-cover-untested-branch.md) walks through finding and fixing a single gap; [TASK-10.33](TASK-10.33-testcontainers-one-test.md) ensures persistence integration tests use real Postgres.

- Official docs: [JaCoCo Maven Plugin](https://www.jacoco.org/jacoco/trunk/doc/maven.html), [JaCoCo — `check` goal](https://www.jacoco.org/jacoco/trunk/doc/check-mojo.html).
