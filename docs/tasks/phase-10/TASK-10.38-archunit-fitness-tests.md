# TASK-10.38 — Architecture fitness tests (ArchUnit)

## Overview

Add the ArchUnit library to `backend/pom.xml` and create a test class `ArchitectureFitnessTest` that encodes four structural rules as executable assertions: the domain layer must not import Spring, JPA, or Lombok; dependencies must only point inward (domain ← application ← adapter/infrastructure); naming conventions must hold (`*Controller` only in `adapter.in.web`, `*JpaEntity` only in `adapter.out.persistence`, `*UseCase` interfaces only in `domain.port.in`); and controllers must depend on use-case in-port interfaces, never on JPA repositories or persistence adapters.

These rules are enforced automatically on every `mvn test` run, turning architectural drift from a code-review concern into a failing build.

---

## Level

Core · Pairs with [TASK-10.35](TASK-10.35-coverage-gate.md) (coverage gate) and [TASK-10.39](TASK-10.39-testcontainers-integration-suite.md) (Testcontainers integration suite)

---

## Why

The hexagonal architecture rule — "domain depends on nothing, adapters depend inward" — is currently enforced only by discipline and code review. A developer under pressure adds a `@Repository` import to a domain service, or calls `UserJpaRepository` directly from a controller to save two lines of code, and nobody catches it until a review comment two days later. ArchUnit turns "don't import Spring in the domain" into a test that fails immediately in the developer's IDE and in CI. The architecture cannot quietly erode because every violation is reported as a test failure with the exact class and import that broke the rule.

---

## Prerequisites

- The backend must build cleanly: `mvn compile` exits with `BUILD SUCCESS`.
- You should understand the project's package structure — re-read `context/project-overview.md` if needed.
- No new tooling required — ArchUnit is a JAR that integrates with JUnit 5.
- Concepts to skim:
  - **ArchUnit** — a Java library for testing architecture rules by analysing compiled bytecode. Rules are written in a fluent Java API. See [archunit.org](https://www.archunit.org/userguide/html/000_Index.html).
  - **`@AnalyzeClasses`** — ArchUnit annotation that tells the test runner which package to scan.
  - **`noClasses().that().resideInAPackage()`** — the fluent API for building "no class in X may import Y" rules.
  - **`layeredArchitecture()`** — ArchUnit's built-in helper for declaring and checking layer dependencies.

---

## Files to Create / Modify

```
backend/pom.xml                                                                                           (modify — add archunit-junit5 dependency)
backend/src/test/java/com/instagram/ArchitectureFitnessTest.java                                          (new)
```

---

## Step-by-Step

### 1. Add the ArchUnit dependency to `pom.xml`

Open `backend/pom.xml` and add inside `<dependencies>`:

```xml
<!-- Architecture fitness tests -->
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.3.0</version>
    <scope>test</scope>
</dependency>
```

> Version 1.3.0 is compatible with Java 21 and JUnit 5.11. Check [Maven Central](https://central.sonatype.com/artifact/com.tngtech.archunit/archunit-junit5) for the latest patch release.

---

### 2. Create the fitness test class

Create the file `backend/src/test/java/com/instagram/ArchitectureFitnessTest.java`.

The class uses `@AnalyzeClasses` to tell ArchUnit to scan the entire `com.instagram` package tree. Each `@ArchTest`-annotated field or method is an individual rule that must hold.

```java
package com.instagram;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

@AnalyzeClasses(packages = "com.instagram")
class ArchitectureFitnessTest {

    // ── Rule 1: Domain purity ─────────────────────────────────────────────── //

    @ArchTest
    static final ArchRule domainMustNotUseSpring =
            noClasses()
                    .that().resideInAPackage("com.instagram.domain..")
                    .should().accessClassesThat().resideInAPackage("org.springframework..")
                    .because("The domain layer must be a pure Java library with no framework dependencies.");

    @ArchTest
    static final ArchRule domainMustNotUseJakartaPersistence =
            noClasses()
                    .that().resideInAPackage("com.instagram.domain..")
                    .should().accessClassesThat().resideInAPackage("jakarta.persistence..")
                    .because("JPA annotations belong in the persistence adapter, not the domain.");

    @ArchTest
    static final ArchRule domainMustNotUseLombok =
            noClasses()
                    .that().resideInAPackage("com.instagram.domain..")
                    .should().accessClassesThat().resideInAPackage("lombok..")
                    .because("Lombok is a build-time annotation processor; the domain must use hand-written builders.");

    // ── Rule 2: Layered dependency direction ─────────────────────────────── //

    @ArchTest
    static final ArchRule layerDependenciesAreRespected =
            layeredArchitecture()
                    .consideringAllDependencies()
                    .layer("Domain").definedBy("com.instagram.domain..")
                    .layer("Application").definedBy("com.instagram.application..")
                    .layer("Adapter").definedBy("com.instagram.adapter..")
                    .layer("Infrastructure").definedBy("com.instagram.infrastructure..")

                    .whereLayer("Domain").mayNotAccessAnyLayer()
                    .whereLayer("Application").mayOnlyAccessLayers("Domain")
                    .whereLayer("Adapter").mayOnlyAccessLayers("Application", "Domain")
                    .whereLayer("Infrastructure").mayOnlyAccessLayers("Domain", "Application", "Adapter")

                    .because("Dependencies must only point inward: adapter/infrastructure → application → domain.");

    // ── Rule 3: Naming conventions ────────────────────────────────────────── //

    @ArchTest
    static final ArchRule controllersMustLiveInAdapterInWeb =
            classes()
                    .that().haveSimpleNameEndingWith("Controller")
                    .should().resideInAPackage("com.instagram.adapter.in.web")
                    .because("All REST controllers must live in the adapter.in.web package.");

    @ArchTest
    static final ArchRule jpaEntitiesMustLiveInPersistence =
            classes()
                    .that().haveSimpleNameEndingWith("JpaEntity")
                    .should().resideInAPackage("com.instagram.adapter.out.persistence..")
                    .because("JPA entity classes must stay in the persistence adapter package and must never be exposed outside it.");

    @ArchTest
    static final ArchRule useCaseInterfacesMustLiveInDomainPortIn =
            classes()
                    .that().haveSimpleNameEndingWith("UseCase")
                    .and().areInterfaces()
                    .should().resideInAPackage("com.instagram.domain.port.in..")
                    .because("Use-case interfaces (in-ports) must live in domain.port.in.");

    @ArchTest
    static final ArchRule jpaRepositoriesMustLiveInPersistence =
            classes()
                    .that().haveSimpleNameEndingWith("JpaRepository")
                    .should().resideInAPackage("com.instagram.adapter.out.persistence..")
                    .because("Spring Data JPA repositories are infrastructure details and must not escape the persistence package.");

    // ── Rule 4: Controllers must not depend on JPA repositories ──────────── //

    @ArchTest
    static final ArchRule controllersMustNotDependOnJpaRepositories =
            noClasses()
                    .that().resideInAPackage("com.instagram.adapter.in.web")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("JpaRepository")
                    .because("Controllers must depend on use-case in-port interfaces, never on JPA repositories directly.");

    @ArchTest
    static final ArchRule controllersMustNotDependOnPersistenceAdapters =
            noClasses()
                    .that().resideInAPackage("com.instagram.adapter.in.web")
                    .should().dependOnClassesThat().haveSimpleNameEndingWith("PersistenceAdapter")
                    .because("Controllers must depend on use-case in-port interfaces, never on persistence adapter implementations.");
}
```

---

### 3. Run the fitness tests

```powershell
cd C:\workspace\Instagram-Social\backend
mvn test "-Dtest=ArchitectureFitnessTest"
```

**Expected output (all rules pass):**

```
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

**If a rule fails**, ArchUnit prints a clear violation message:

```
java.lang.AssertionError: Architecture Violation [Priority: MEDIUM] - Rule 'no classes that
  reside in a package 'com.instagram.domain..' should access classes that reside in a package
  'org.springframework..' because The domain layer must be a pure Java library with no framework dependencies.'
was violated (1 times):
  Class <com.instagram.domain.service.PostService> accesses class <org.springframework.stereotype.Service>
  in (PostService.java:7)
```

Fix the violation in the source class, then re-run.

---

### 4. Understand the `layeredArchitecture()` rule and allowed exceptions

The `layeredArchitecture()` rule can be strict about certain Spring-internal or test-only relationships. If you see violations from infrastructure config or integration test classes, scope the rule more narrowly:

```java
@ArchTest
static final ArchRule layerDependenciesAreRespected =
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()   // ignores dependencies TO/FROM layers not listed
                .layer("Domain").definedBy("com.instagram.domain..")
                // ... same as above
```

Switch from `consideringAllDependencies()` to `consideringOnlyDependenciesInLayers()` if you get spurious failures from third-party library classes.

---

### 5. Verify the rules are part of the normal test run

Run the full test suite:

```powershell
cd C:\workspace\Instagram-Social\backend
mvn test
```

`ArchitectureFitnessTest` will appear in the normal test output alongside unit and integration tests. No separate command is needed — `mvn test` and `mvn verify` both include it.

---

### 6. Intentionally violate a rule to confirm it fails

As a sanity check, temporarily add a Spring import to a domain model:

1. Open `backend/src/main/java/com/instagram/domain/model/Report.java`.
2. Add this unused import at the top: `import org.springframework.stereotype.Component;`
3. Run `mvn test -Dtest=ArchitectureFitnessTest`.
4. Confirm the test fails with a violation for `Report`.
5. Remove the import and verify the test passes again.

---

## Checklist

- [ ] Add the `com.tngtech.archunit:archunit-junit5` dependency (test scope) to `pom.xml`
- [ ] Rule: no class in `domain..` imports `org.springframework..`, `jakarta.persistence..`, or `lombok..`
  - [ ] `domainMustNotUseSpring` passes
  - [ ] `domainMustNotUseJakartaPersistence` passes
  - [ ] `domainMustNotUseLombok` passes
- [ ] Rule: dependencies only point inward (`domain` ← `application` ← `adapter`/`infrastructure`); no outward edges from the domain
  - [ ] `layerDependenciesAreRespected` passes for all four layers
- [ ] Rule: naming conventions hold (`*Controller` in `adapter.in.web`, `*JpaEntity` only in `persistence`, `*UseCase` interfaces in `domain.port.in`)
  - [ ] `controllersMustLiveInAdapterInWeb` passes
  - [ ] `jpaEntitiesMustLiveInPersistence` passes
  - [ ] `useCaseInterfacesMustLiveInDomainPortIn` passes
  - [ ] `jpaRepositoriesMustLiveInPersistence` passes
- [ ] Rule: controllers depend on use-case in-ports, never on persistence adapters or `*JpaRepository`
  - [ ] `controllersMustNotDependOnJpaRepositories` passes
  - [ ] `controllersMustNotDependOnPersistenceAdapters` passes
- [ ] Intentional violation test confirms a rule correctly fails when a Spring import is added to the domain
- [ ] `mvn test` includes `ArchitectureFitnessTest` in the normal run without extra configuration

---

## How to Verify

```powershell
cd C:\workspace\Instagram-Social\backend
mvn test "-Dtest=ArchitectureFitnessTest"
```

Passing result:

```
[INFO] Tests run: 9, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.21s
[INFO] BUILD SUCCESS
```

To run as part of the full build (recommended):

```powershell
mvn verify
```

The architecture tests run in the `test` phase, before the JaCoCo coverage gate in the `verify` phase, so a layering violation will stop the build before coverage is even checked.

---

## Notes / Gotchas

- **`@AnalyzeClasses` scans compiled bytecode, not source** — ArchUnit reads from `target/classes`. The classes must be compiled before the test runs. `mvn test` compiles first, so this is automatic. If you run the test from an IDE without compiling first, you may get `ClassFileImporter` errors.

- **`consideringAllDependencies()` vs `consideringOnlyDependenciesInLayers()`** — The former reports violations from any dependency on or from a layer, including to classes outside the declared layers (e.g., Java stdlib, Spring itself). If this is too noisy, switch to the latter, which only checks dependencies between your declared layers.

- **Domain `event/` sub-package** — The `domain/event/` sub-package (e.g., `NotificationEvent.java`) must also remain Spring-free. `@AnalyzeClasses(packages = "com.instagram")` covers it automatically.

- **`MessageWebSocketController`** — This controller lives in `com.instagram.adapter.in.messaging`, not `adapter.in.web`. The `controllersMustLiveInAdapterInWeb` rule will fail for it. Either exclude it explicitly with `.ignoreDependency()` or relax the rule to check `adapter.in..` instead:

  ```java
  .should().resideInAPackage("com.instagram.adapter.in..")
  ```

  Update the `because` message accordingly.

- **`@Service` on application services** — Application services (e.g., `PostService`, `UserService`) are in `com.instagram.application.service` and carry `@Service`. This is fine — the Spring annotation is in `application`, not `domain`. The rule only forbids Spring imports in the `domain..` package.

- **ArchUnit version** — 1.3.0 is the recommended version as of early 2026. Check [Maven Central](https://central.sonatype.com/artifact/com.tngtech.archunit/archunit-junit5) before pinning a different version.

- Official docs: [ArchUnit User Guide](https://www.archunit.org/userguide/html/000_Index.html), [ArchUnit — `layeredArchitecture()`](https://www.archunit.org/userguide/html/000_Index.html#_layer_checks).
