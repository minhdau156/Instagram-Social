# TASK-10.34 — Cover one untested branch

## Overview

Open the JaCoCo HTML coverage report, find one branch that is highlighted red (not executed by any test), write a targeted test that triggers that branch, and re-run the report to confirm the branch turns green. This task is a single concrete exercise in the core skill behind the coverage gate in [TASK-10.35](TASK-10.35-coverage-gate.md): reading a report, pinpointing a gap, and writing the minimum test needed to close it.

Exception-handler branches in `GlobalExceptionHandler` and guard conditions inside domain services are the most common sources of uncovered branches at this stage of the project.

---

## Level

Warm-up · Pairs with [TASK-10.35](TASK-10.35-coverage-gate.md)

---

## Why

A coverage report is only useful if you act on what it tells you. Reading a red branch, understanding why it is not tested, and writing the test that covers it is the concrete skill that powers the automated gate in TASK-10.35. Doing it manually once makes you faster at identifying and closing coverage gaps when `mvn verify` rejects a pull request.

The JaCoCo branch metric is more meaningful than the line metric because a line like `if (x == null) throw new FooException()` is counted as "covered" the moment any test reaches the line — even if no test ever makes `x` null and triggers the `throw`. Only branch coverage reveals that the exceptional path is untested.

---

## Prerequisites

- [TASK-10.35](TASK-10.35-coverage-gate.md) requires you to reach 80% branch coverage overall; this warm-up gives you hands-on practice first.
- The backend must build cleanly: `cd backend && mvn compile` should exit with `BUILD SUCCESS`.
- Concepts to skim:
  - **JaCoCo HTML report** — generated at `backend/target/site/jacoco/index.html` after `mvn verify` or `mvn test jacoco:report`. Red diamonds = branches not covered; yellow = partially covered; green = fully covered.
  - **Branch coverage** — JaCoCo counts each `if`/`else`, ternary, and `switch` arm as a separate branch. A branch is "covered" only when a test actually executes that arm.
  - **JUnit 5 `assertThrows`** — used to assert that a method throws a specific exception, which is the usual way to cover an exception branch.

---

## Files to Create / Modify

```
backend/pom.xml                                                                      (modify — add JaCoCo plugin if not present)
backend/src/test/java/com/instagram/application/service/<AnyServiceTest>.java        (modify — add one new test method)
```

The exact test file depends on which uncovered branch you choose. The examples below use `UserServiceTest` and `GlobalExceptionHandler`, but your branch may be in a different class.

---

## Step-by-Step

### 1. Generate the coverage report

From the `backend/` directory, run all tests and produce the HTML report:

```powershell
cd C:\workspace\Instagram-Social\backend
mvn test jacoco:report
```

If `jacoco:report` is not yet configured you will see `No plugin found for prefix 'jacoco'`. In that case, add the JaCoCo Maven plugin to `backend/pom.xml` first (the same configuration used in [TASK-10.35](TASK-10.35-coverage-gate.md)):

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>
        <execution>
            <id>report</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
        </execution>
    </executions>
</plugin>
```

Then re-run `mvn test jacoco:report`.

---

### 2. Open the report and find a red branch

Open the report in a browser:

```powershell
Start-Process "C:\workspace\Instagram-Social\backend\target\site\jacoco\index.html"
```

Navigate by clicking package names down to class level. Good places to look:

- `com.instagram.adapter.in.web.GlobalExceptionHandler` — each `@ExceptionHandler` method is a separate branch; any exception type that has no test throwing it will be red.
- `com.instagram.application.service.*` — guard conditions like `if (!post.getUserId().equals(userId)) throw UnauthorizedPostAccessException` are often missed.
- `com.instagram.domain.model.Report` — the `withResolved`/`withDismissed`/`withReviewed` terminal-state guards throw `IllegalStateException` and are easy to miss.

Click on a red diamond next to a specific line. JaCoCo will show you exactly which branch (true or false) was never taken.

**Example**: In `GlobalExceptionHandler`, the handler for `ReportNotFoundException` might show as a red diamond because no test ever throws that exception through the HTTP layer.

---

### 3. Understand why the branch is uncovered

Read the source of the class that contains the red branch. Ask yourself: "What input or condition would cause execution to reach this branch?" Common patterns:

- An exception-handler method in `GlobalExceptionHandler` — you need a test that calls an endpoint whose service throws the corresponding domain exception.
- An `if (x == null)` guard at the top of a service method — you need to call the method with `null`.
- A status guard like `if (status == RESOLVED || status == DISMISSED) throw ...` — you need to call the business method when the object is already in that terminal state.

---

### 4. Write the targeted test

Add one test method to the relevant `*Test.java` file. The test should be the minimum necessary to exercise the uncovered branch — do not add unrelated assertions.

**Example A — covering a `GlobalExceptionHandler` branch via a controller integration test:**

In `backend/src/test/java/com/instagram/adapter/in/web/PostControllerIT.java`, add:

```java
@Test
void getPost_returnsNotFound_whenPostDoesNotExist() throws Exception {
    UUID unknownId = UUID.randomUUID();
    // getPostUseCase is a MockBean; configure it to throw the domain exception
    given(getPostUseCase.getPost(any())).willThrow(new PostNotFoundException(unknownId));

    mockMvc.perform(get("/api/v1/posts/{id}", unknownId)
                    .header("Authorization", "Bearer " + validToken))
            .andExpect(status().isNotFound());
}
```

**Example B — covering a domain model guard in `Report` (unit test):**

In `backend/src/test/java/com/instagram/domain/model/ReportTest.java` (create if it does not exist):

```java
package com.instagram.domain.model;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportTest {

    @Test
    void withResolved_throwsIllegalState_whenAlreadyResolved() {
        Report report = Report.builder()
                .reporterId(UUID.randomUUID())
                .entityType(ReportEntityType.POST)
                .entityId(UUID.randomUUID())
                .reason("spam")
                .createdAt(OffsetDateTime.now())
                .build()
                .withResolved(UUID.randomUUID()); // transition to RESOLVED

        UUID anotherReviewer = UUID.randomUUID();

        // Attempting a second transition from a terminal state must throw
        assertThatThrownBy(() -> report.withResolved(anotherReviewer))
                .isInstanceOf(IllegalStateException.class);
    }
}
```

**Example C — covering a service guard condition (unit test):**

In `backend/src/test/java/com/instagram/application/service/UserServiceTest.java`, add:

```java
@Test
void updateUser_throwsUnauthorized_whenCallerIsNotOwner() {
    UUID ownerId = UUID.randomUUID();
    UUID callerId = UUID.randomUUID(); // different user
    User existingUser = User.builder().id(ownerId).username("owner").build();
    given(userRepository.findById(ownerId)).willReturn(Optional.of(existingUser));

    assertThatThrownBy(() ->
            userService.updateUser(new UpdateUserUseCase.Command(ownerId, callerId, "new bio", null))
    ).isInstanceOf(UnauthorizedPostAccessException.class); // or the appropriate exception
}
```

---

### 5. Re-run the report and confirm coverage

```powershell
cd C:\workspace\Instagram-Social\backend
mvn test jacoco:report
```

Reopen `target/site/jacoco/index.html`, navigate to the same class, and confirm the previously red diamond is now green (or at minimum yellow, indicating the branch is partially covered). If the diamond is still red, the test did not actually execute that path — add a debug `System.out.println` temporarily to confirm the code path is reached, then remove it.

---

## Checklist

- [ ] Open the JaCoCo HTML report and find one uncovered exception branch
  - [ ] Run `mvn test jacoco:report` to generate `target/site/jacoco/index.html`
  - [ ] Navigate to a class with a red or yellow branch diamond
  - [ ] Identify the exact branch condition that is never executed
- [ ] Write a test that triggers that branch
  - [ ] The test is in the correct file (`*Test.java` for unit, `*IT.java` for integration)
  - [ ] The test uses `assertThatThrownBy` or `mockMvc.perform(...).andExpect(status().isXxx())` as appropriate
  - [ ] The test name clearly describes what it asserts (e.g., `withResolved_throwsIllegalState_whenAlreadyResolved`)
- [ ] Re-run and confirm the branch is now covered
  - [ ] `mvn test jacoco:report` succeeds
  - [ ] The previously red diamond for that branch is now green in the HTML report

---

## How to Verify

Run the full test suite and regenerate the report:

```powershell
cd C:\workspace\Instagram-Social\backend
mvn verify
Start-Process "C:\workspace\Instagram-Social\backend\target\site\jacoco\index.html"
```

Passing result: the test suite exits with `BUILD SUCCESS` and the branch you targeted is no longer highlighted red in the HTML report. The overall branch coverage percentage shown on the index page may tick up by a fraction of a percent — this is expected from a single branch fix.

---

## Notes / Gotchas

- **Line coverage vs. branch coverage** — You may see a line covered (black text) but a branch still red (red diamond). This happens when an `if` condition is reached but only one of its two arms is ever taken. JaCoCo counts each arm separately. Always look at the diamond, not just the line colour.

- **`assertThatThrownBy` vs `assertThrows`** — Both work; `assertThatThrownBy` from AssertJ gives a more readable fluent API: `assertThatThrownBy(() -> ...).isInstanceOf(FooException.class).hasMessageContaining("...")`. Use whichever the existing test file already uses for consistency.

- **`@WebMvcTest` vs `@SpringBootTest`** — Controller integration tests in this project use `@WebMvcTest` with `MockBean`s. To trigger a `GlobalExceptionHandler` branch you need to configure the relevant MockBean to throw the domain exception: `given(useCase.method(any())).willThrow(new DomainException(...))`.

- **Branches in lambdas** — JaCoCo sometimes cannot instrument branches inside lambda expressions correctly. If a branch in a lambda remains red despite a test reaching it, it is a known JaCoCo limitation and not something to fix here.

- Related tasks: [TASK-10.35](TASK-10.35-coverage-gate.md) adds the automated gate that enforces this discipline on every pull request.

- Official docs: [JaCoCo — Understanding the Report](https://www.jacoco.org/jacoco/trunk/doc/index.html), [JUnit 5 — `assertThrows`](https://junit.org/junit5/docs/current/user-guide/#writing-tests-assertions).

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Line vs branch coverage** — covering a line isn't covering both `if` paths — https://www.jacoco.org/jacoco/trunk/doc/counters.html
- **Reading a JaCoCo report** — red/yellow/green and missed branches — https://www.jacoco.org/jacoco/trunk/doc/
- **Writing focused unit tests** — one behaviour per test, arrange-act-assert — https://junit.org/junit5/docs/current/user-guide/

### Official docs (code reference)
- **JUnit 5 user guide** — https://junit.org/junit5/docs/current/user-guide/
- **JaCoCo documentation** — https://www.jacoco.org/jacoco/trunk/doc/
