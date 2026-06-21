# TASK-10.27 — Actuator & Micrometer

## Overview

Add Spring Boot Actuator and Micrometer's Prometheus registry to the backend, then instrument three high-value application events — post creations, likes, and user registrations — with custom counters. Actuator exposes the health endpoint (`/actuator/health`) and the Prometheus scrape endpoint (`/actuator/prometheus`). Micrometer acts as the vendor-neutral metrics facade: your code increments a counter once, and Micrometer translates it into the Prometheus format automatically. The custom counters you define here become the raw material for the Grafana dashboard in TASK-10.29.

---

## Level

Core · Builds toward [TASK-10.29 — Grafana dashboards & Prometheus alerting](TASK-10.29-grafana-prometheus-alerting.md) · Pairs with [TASK-10.28 — Distributed tracing](TASK-10.28-distributed-tracing.md)

---

## Why

You can't operate what you can't see. When `POST /api/v1/posts` starts failing at 3 am, the first thing you want to know is "how many requests per minute are hitting that endpoint and how many are succeeding?" Without metrics, you are flying blind: you can only discover the problem from user complaints, not from a firing alert. Health and metrics endpoints expose the app's internal state — connection pool saturation, JVM heap, HTTP error rates, and your custom business counters — so monitoring tools (and you) can spot trouble early, before users do.

Custom counters go further: they answer business questions ("how many posts were created in the last hour?") that generic HTTP metrics cannot. They are also the inputs to your SLO calculations in TASK-10.31.

---

## Prerequisites

- The backend compiles and the local profile starts cleanly.
- [TASK-10.26](TASK-10.26-structured-logging-mdc.md) is complete — the structured logging setup is in place, which means the Actuator security configuration you add here will coexist cleanly with `MdcLoggingFilter`.
- Basic familiarity with `pom.xml` dependency management in Maven.

**Concepts to skim:**

- **Spring Boot Actuator**: a Spring Boot sub-project that auto-configures `/actuator/*` endpoints exposing health, metrics, info, environment, and more.
- **Micrometer**: the metrics facade that Spring Boot Actuator uses under the hood. You write `meterRegistry.counter("posts.created").increment()` once, and Micrometer publishes it to whatever backend is on the classpath (Prometheus, Datadog, CloudWatch, etc.).
- **Prometheus scrape model**: Prometheus periodically pulls (`scrapes`) the `/actuator/prometheus` endpoint and stores the time-series data. The app does not push metrics anywhere — Prometheus comes and fetches them.
- **Counter vs Gauge vs Timer**: a Counter only goes up (events counted), a Gauge can go up and down (current pool size), a Timer measures duration and count together. Use a Counter for "how many times did X happen" and a Timer for "how long did X take."

---

## Files to Create / Modify

```
backend/pom.xml                                                                    (modify)
backend/src/main/resources/application.yml                                         (modify)
backend/src/main/java/com/instagram/infrastructure/config/MetricsConfig.java       (new)
backend/src/main/java/com/instagram/application/service/PostService.java           (modify)
backend/src/main/java/com/instagram/application/service/LikeService.java           (modify — if it exists; otherwise the domain service that handles likes)
backend/src/main/java/com/instagram/application/service/UserService.java           (modify)
backend/src/main/java/com/instagram/infrastructure/security/SecurityConfig.java    (modify)
```

---

## Step-by-Step

### 1. Add Actuator and Micrometer dependencies to pom.xml

Open `backend/pom.xml` and add these two dependencies inside `<dependencies>`. Both are managed by the Spring Boot parent BOM, so no version is needed:

```xml
<!-- Actuator: /actuator/health, /actuator/metrics, /actuator/prometheus -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Prometheus registry: translates Micrometer metrics into the Prometheus scrape format -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

---

### 2. Expose the required Actuator endpoints in application.yml

Add a `management:` block to `backend/src/main/resources/application.yml`. Place it at the top level, alongside the existing `server:`, `spring:`, and `app:` keys:

```yaml
management:
  endpoints:
    web:
      exposure:
        # Expose these endpoints over HTTP. Keep the list explicit — never use '*' in prod.
        include: health, info, metrics, prometheus
  endpoint:
    health:
      show-details: when-authorized   # Only admins see full health details
  metrics:
    tags:
      application: ${spring.application.name}  # Adds app="instagram" label to every metric
```

> **Why `show-details: when-authorized`?** The `/actuator/health` endpoint in its default form returns only `{"status":"UP"}`. With `when-authorized`, authenticated admin users additionally see database connectivity, disk space, and JVM status — useful for debugging but not exposed to anonymous callers.

---

### 3. Secure Actuator endpoints in SecurityConfig.java

The Actuator endpoints are currently unprotected. Open `backend/src/main/java/com/instagram/infrastructure/security/SecurityConfig.java` and add Actuator rules to the `authorizeHttpRequests` chain. Insert the new matchers **before** the existing `anyRequest().authenticated()` line:

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/v1/auth/**").permitAll()
    .requestMatchers("/api/v1/users/{username}").permitAll()
    .requestMatchers("/swagger-ui/**").permitAll()
    .requestMatchers("/v3/api-docs/**").permitAll()
    .requestMatchers("/swagger-ui.html").permitAll()
    .requestMatchers("/oauth2/**").permitAll()
    .requestMatchers("/login/oauth2/**").permitAll()
    .requestMatchers("/ws/**").permitAll()
    // Actuator: health is public (used by load balancers); everything else requires ADMIN
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/actuator/**").hasRole("ADMIN")
    .anyRequest().authenticated()
)
```

> **Note on `ROLE_ADMIN`:** The current user model stores a `role` field on the `User` domain entity. Verify the role values in `UserJpaEntity` before deploying. If the project uses `ROLE_USER` and `ROLE_ADMIN` as string authorities, the `hasRole("ADMIN")` matcher works as written. If roles have not been wired into Spring Security yet, you can temporarily use `authenticated()` for the non-health Actuator endpoints and leave a `// TODO: restrict to ADMIN` comment.

---

### 4. Create MetricsConfig.java

Create `backend/src/main/java/com/instagram/infrastructure/config/MetricsConfig.java`. This class declares the named counters as Spring beans so they can be injected wherever they are needed, rather than creating ad-hoc counters inline.

```java
package com.instagram.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers named Micrometer counters as Spring beans.
 *
 * Naming convention: noun_verb (snake_case), e.g. posts_created_total.
 * Prometheus will suffix _total automatically for Counter types.
 */
@Configuration
public class MetricsConfig {

    /**
     * Incremented each time a post is successfully persisted.
     * Exposed as: posts_created_total{application="instagram"}
     */
    @Bean
    public Counter postsCreatedCounter(MeterRegistry registry) {
        return Counter.builder("posts.created")
                .description("Number of posts successfully created")
                .tag("feature", "posts")
                .register(registry);
    }

    /**
     * Incremented each time a like is toggled on (not off).
     * Exposed as: likes_added_total{application="instagram"}
     */
    @Bean
    public Counter likesAddedCounter(MeterRegistry registry) {
        return Counter.builder("likes.added")
                .description("Number of likes added (not removed)")
                .tag("feature", "interactions")
                .register(registry);
    }

    /**
     * Incremented each time a new user successfully registers.
     * Exposed as: users_registered_total{application="instagram"}
     */
    @Bean
    public Counter usersRegisteredCounter(MeterRegistry registry) {
        return Counter.builder("users.registered")
                .description("Number of new user registrations")
                .tag("feature", "auth")
                .register(registry);
    }
}
```

---

### 5. Instrument PostService

Open `backend/src/main/java/com/instagram/application/service/PostService.java`. Inject `postsCreatedCounter` and call `increment()` after a post is successfully saved.

Add the field (constructor injection — no `@Autowired`):

```java
private final Counter postsCreatedCounter;
```

Update the constructor to accept the counter. For example, after the existing parameters:

```java
public PostService(PostRepository postRepository,
                   PostMediaRepository postMediaRepository,
                   HashtagRepository hashtagRepository,
                   MediaStoragePort mediaStoragePort,
                   LikeRepository likeRepository,
                   SavedPostRepository savedPostRepository,
                   PostHashtagRepository postHashtagRepository,
                   Counter postsCreatedCounter) {   // <-- add this
    // ... existing assignments ...
    this.postsCreatedCounter = postsCreatedCounter;
}
```

Inside `createPost()`, after the call to `postRepository.save(post)`:

```java
postsCreatedCounter.increment();
log.info("Post created id={} userId={}", savedPost.getId(), command.userId());
```

---

### 6. Instrument the like service

Find the service class that implements the `LikePostUseCase` (likely `LikeService` in `domain/service/` or `application/service/`). Inject `likesAddedCounter` and call `increment()` after a like is successfully recorded:

```java
private final Counter likesAddedCounter;

// In the constructor — add likesAddedCounter parameter

// In the likePost() method, after the like is saved:
likesAddedCounter.increment();
```

---

### 7. Instrument UserService for registrations

Open the service class that implements user registration (likely `UserService` in `application/service/`). Inject `usersRegisteredCounter` and call `increment()` after a new user is persisted:

```java
private final Counter usersRegisteredCounter;

// In the constructor — add usersRegisteredCounter parameter

// In the registerUser() / createUser() method, after persistence:
usersRegisteredCounter.increment();
```

---

### 8. Start the backend and verify the Actuator endpoints

```powershell
cd backend
mvn spring-boot:run
```

**Check the health endpoint (no auth required):**

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/health"
```

Expected response:

```json
{"status":"UP"}
```

**Check the Prometheus scrape endpoint (requires login; use a valid JWT):**

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/prometheus" `
  -Headers @{ Authorization = "Bearer $token" }
```

In the output, search for your custom metric names:

```
# HELP posts_created_total Number of posts successfully created
# TYPE posts_created_total counter
posts_created_total{application="instagram",feature="posts"} 0.0
```

They start at `0.0`. Create a post and re-hit the endpoint — the value should become `1.0`.

---

### 9. Verify the counters increment

```powershell
# Create a post (reuse the token from TASK-10.25/26)
Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/posts" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body '{"caption":"metrics test","mediaItems":[{"objectKey":"k","mediaType":"IMAGE","displayOrder":0}]}'

# Re-check prometheus
Invoke-RestMethod -Uri "http://localhost:8080/actuator/prometheus" `
  -Headers @{ Authorization = "Bearer $token" } |
  Select-String "posts_created_total"
```

Expected output:

```
posts_created_total{application="instagram",feature="posts"} 1.0
```

---

## Checklist

- [x] Add `spring-boot-starter-actuator` + `micrometer-registry-prometheus` to `pom.xml`
- [x] Expose `health`, `info`, `metrics`, `prometheus` endpoints
- [x] Add custom `MeterRegistry` counter for post creations, likes, registrations
- [x] Secure Actuator endpoints (allow only `ROLE_ADMIN` except `/actuator/health`)

---

## How to Verify

**1. `/actuator/health` returns `UP` without authentication:**

```powershell
(Invoke-RestMethod -Uri "http://localhost:8080/actuator/health").status
```

Expected: `UP`

**2. `/actuator/prometheus` lists your custom counters:**

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/actuator/prometheus" `
  -Headers @{ Authorization = "Bearer $token" } |
  Select-String -Pattern "posts_created_total|likes_added_total|users_registered_total"
```

Expected: three lines, each with `# HELP`, `# TYPE`, and a value line.

**3. Counters increment when you create a post / like / register:**

Create one post, one like, and one new user registration. Re-hit `/actuator/prometheus` and confirm the corresponding counters are each `1.0` (or higher if you did multiple actions).

**4. `/actuator/prometheus` is rejected without authentication:**

```powershell
try {
    Invoke-RestMethod -Uri "http://localhost:8080/actuator/prometheus"
} catch {
    $_.Exception.Response.StatusCode
}
```

Expected: `401` (Unauthorized) — confirming the endpoint is protected.

---

## Notes / Gotchas

**"Counter values reset every time I restart the app."**
Micrometer counters are in-memory. They reset on restart. That is expected — Prometheus stores the historical data in its own time-series database (added in TASK-10.29). The app's counters are the current-state view; Prometheus owns the history.

**"My custom counter name has dots but Prometheus uses underscores."**
Micrometer automatically translates dots to underscores for Prometheus. `posts.created` becomes `posts_created_total` in the scrape output. The `_total` suffix is appended automatically by the Prometheus registry for all counter types.

**"The `postsCreatedCounter` bean cannot be found — Spring says 'No qualifying bean'."**
You may have multiple constructors or the bean name does not match. Spring matches by parameter type (`Counter`) and name. If you have more than one `Counter` bean, annotate the injection point with `@Qualifier("postsCreatedCounter")`.

**"I see `management.endpoints.web.exposure.include=*` recommended elsewhere — why not use that?"**
`*` exposes all endpoints including `env`, `heapdump`, `threaddump`, and `shutdown` (if enabled). In any environment that is network-accessible, these are serious security risks. Always use an explicit list.

**"Where do the JVM heap and HikariCP metrics come from?"**
They come from Micrometer's auto-configured binders. When `micrometer-registry-prometheus` is on the classpath, Spring Boot auto-registers `JvmMetrics`, `ProcessMetrics`, and `HikariCP` metrics automatically — you do not need to write any code. They appear in the `/actuator/prometheus` output as `jvm_*`, `process_*`, and `hikaricp_*` prefixes.

**References:**
- [Spring Boot Actuator docs](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html)
- [Micrometer Prometheus registry](https://docs.micrometer.io/micrometer/reference/implementations/prometheus.html)
- [Micrometer Counter API](https://docs.micrometer.io/micrometer/reference/concepts/counters.html)

**Cross-task references:**
- [TASK-10.29](TASK-10.29-grafana-prometheus-alerting.md) — Prometheus scrapes this endpoint; Grafana panels use these counters
- [TASK-10.31](TASK-10.31-sli-slo-error-budget.md) — SLI ratios are computed from `http_server_requests` (auto-instrumented by Actuator)

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Spring Boot Actuator** — built-in health, info, and metrics endpoints — https://spring.io/guides/gs/actuator-service/
- **Micrometer meters** — counters, gauges, timers, distribution summaries — https://docs.micrometer.io/micrometer/reference/
- **The /actuator/prometheus endpoint** — exposing metrics for scraping — https://prometheus.io/docs/introduction/overview/

### Official docs (code reference)
- **Micrometer reference** — https://docs.micrometer.io/micrometer/reference/
- **Spring Boot (project page → Actuator)** — https://spring.io/projects/spring-boot
