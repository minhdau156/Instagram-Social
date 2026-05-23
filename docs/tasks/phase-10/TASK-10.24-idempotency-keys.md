# TASK-10.24 — Idempotency keys for unsafe POSTs

## Overview

Implement idempotency for the `POST /api/v1/posts` (create post) and `POST /api/v1/messages` (send message) endpoints. The client sends a UUID in the `Idempotency-Key` header with every request. If the backend receives the same key twice, it returns the stored response from the first call without executing the operation again. This makes POST requests safe to retry — a network drop or timeout becomes a non-event instead of a duplicate post.

---

## Level

**Stretch** — builds on [TASK-10.21 (Object-level authorization)](TASK-10.21-object-level-authorization-idor.md) (correct identity handling) and [TASK-10.46 (Full docker-compose.yml)](TASK-10.46-docker-compose.md) (the infrastructure this runs on in production).

---

## Why

HTTP GET requests are naturally idempotent — you can repeat them safely. POST requests are not. When a mobile app creates a post and the response times out in transit, the client has no way to know whether the server received the request. The safe choice for the client is to retry; the result is a duplicate post. Idempotency keys solve this by letting the client say "I already sent this request under this key — if you processed it, give me the result again." The key is the client's responsibility to generate (a UUID); the storage and short-circuit logic is the server's responsibility. This pattern is used by Stripe, Braintree, and virtually every financial API precisely because retries are unavoidable in distributed systems.

---

## Prerequisites

- Flyway migrations through `V3__add_fts_indexes.sql` are the most recent — the new migration will be `V4__idempotency_keys.sql`.
- `AuthController`, `PostController`, `MessageController` are in place.
- Understanding of Spring's `HandlerInterceptor` or `OncePerRequestFilter` — the idempotency guard runs as middleware before the controller method.
- **Concept gloss:**
  - **Idempotency key** — a client-generated UUID that uniquely identifies one logical operation. If the server receives the same key twice, it returns the first response without repeating the work.
  - **Request hash** — a hash of the request body. If the same key arrives with a different body, the operation is ambiguous; the server returns `409 Conflict`.
  - **`HandlerInterceptor`** — a Spring MVC hook that runs before (`preHandle`) and after (`afterCompletion`) every controller method.

---

## Files to Create / Modify

```
backend/src/main/resources/db/migration/V4__idempotency_keys.sql                              (new)
backend/src/main/java/com/instagram/adapter/out/persistence/entity/IdempotencyKeyJpaEntity.java (new)
backend/src/main/java/com/instagram/adapter/out/persistence/repository/IdempotencyKeyJpaRepository.java (new)
backend/src/main/java/com/instagram/infrastructure/config/IdempotencyInterceptor.java           (new)
backend/src/main/java/com/instagram/infrastructure/config/WebMvcConfig.java                     (new or modify)
backend/src/test/java/com/instagram/adapter/in/web/IdempotencyIT.java                           (new)
```

---

## Step-by-Step

### 1. Create the Flyway migration

Create `backend/src/main/resources/db/migration/V4__idempotency_keys.sql`:

```sql
-- Stores the result of idempotent POST operations.
-- The `key` is supplied by the client as a UUID header value.
-- `request_hash` detects whether the same key was sent with a different body (conflict).
-- `response_body` is the JSON response body stored on first execution.
-- `http_status` is the HTTP status code of the first response.
-- `created_at` is used for TTL cleanup (TASK-10.48 scheduled job can purge old rows).

CREATE TABLE idempotency_keys (
    key             UUID        NOT NULL PRIMARY KEY,
    user_id         UUID        NOT NULL,
    endpoint        VARCHAR(200) NOT NULL,
    request_hash    VARCHAR(64) NOT NULL,
    response_body   TEXT        NOT NULL,
    http_status     INT         NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for efficient cleanup of old keys
CREATE INDEX idx_idempotency_created_at ON idempotency_keys (created_at);

-- Index for user-scoped lookups (optional, for auditing)
CREATE INDEX idx_idempotency_user ON idempotency_keys (user_id, created_at);
```

### 2. Create the JPA entity

```java
package com.instagram.adapter.out.persistence.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "idempotency_keys")
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyKeyJpaEntity {

    @Id
    @Column(name = "key", nullable = false, updatable = false)
    private UUID key;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "http_status", nullable = false)
    private int httpStatus;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
```

### 3. Create the JPA repository

```java
package com.instagram.adapter.out.persistence.repository;

import com.instagram.adapter.out.persistence.entity.IdempotencyKeyJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyJpaRepository extends JpaRepository<IdempotencyKeyJpaEntity, UUID> {

    Optional<IdempotencyKeyJpaEntity> findByKeyAndUserId(UUID key, UUID userId);
}
```

### 4. Create `IdempotencyInterceptor`

The interceptor runs before and after each request. In `preHandle` it checks whether the key has been seen before. In `afterCompletion` it stores the response for successful first-time requests.

```java
package com.instagram.infrastructure.config;

import com.instagram.adapter.out.persistence.entity.IdempotencyKeyJpaEntity;
import com.instagram.adapter.out.persistence.repository.IdempotencyKeyJpaRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

/**
 * Enforces idempotency for POST endpoints that declare "X-Idempotent: true"
 * (set as a request attribute by the controller, or matched by path pattern).
 *
 * Flow:
 *  preHandle  — if key exists and hashes match, write stored response and halt.
 *               if key exists and hashes differ, write 409 and halt.
 *  afterCompletion — if key is new and response is 2xx, store key + response.
 */
@Component
public class IdempotencyInterceptor implements HandlerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyInterceptor.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private final IdempotencyKeyJpaRepository repository;

    public IdempotencyInterceptor(IdempotencyKeyJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String rawKey = request.getHeader(IDEMPOTENCY_KEY_HEADER);
        if (rawKey == null || rawKey.isBlank()) {
            return true;  // no key supplied — pass through normally
        }

        UUID key;
        try {
            key = UUID.fromString(rawKey);
        } catch (IllegalArgumentException e) {
            response.setStatus(400);
            response.setContentType("application/json");
            response.getWriter().write(
                "{\"data\":null,\"error\":\"Idempotency-Key must be a valid UUID\"}");
            return false;
        }

        UUID userId = currentUserId();
        if (userId == null) {
            return true;  // unauthenticated — let security filter handle it
        }

        String requestHash = hashRequestBody(request);
        Optional<IdempotencyKeyJpaEntity> stored = repository.findByKeyAndUserId(key, userId);

        if (stored.isPresent()) {
            IdempotencyKeyJpaEntity record = stored.get();
            if (!record.getRequestHash().equals(requestHash)) {
                // Same key, different body — this is a conflict
                response.setStatus(409);
                response.setContentType("application/json");
                response.getWriter().write(
                    "{\"data\":null,\"error\":\"Idempotency key was already used with a different request body\"}");
                log.warn("Idempotency key {} reused with different payload by user {}", key, userId);
                return false;
            }
            // Same key, same body — return the stored response
            response.setStatus(record.getHttpStatus());
            response.setContentType("application/json");
            response.getWriter().write(record.getResponseBody());
            log.debug("Idempotency key {} already processed — returning cached response", key);
            return false;
        }

        // First time seeing this key — store it in request scope for afterCompletion
        request.setAttribute("idempotencyKey", key);
        request.setAttribute("idempotencyUserId", userId);
        request.setAttribute("idempotencyHash", requestHash);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                Exception ex) {

        UUID key    = (UUID) request.getAttribute("idempotencyKey");
        UUID userId = (UUID) request.getAttribute("idempotencyUserId");
        String hash = (String) request.getAttribute("idempotencyHash");

        if (key == null || userId == null) {
            return;  // no idempotency key was set for this request
        }

        int status = response.getStatus();
        if (status < 200 || status >= 300) {
            return;  // only cache successful responses
        }

        // Read the response body (requires ContentCachingResponseWrapper — see WebMvcConfig)
        String responseBody = "";
        if (response instanceof ContentCachingResponseWrapper wrapper) {
            responseBody = new String(wrapper.getContentAsByteArray(), StandardCharsets.UTF_8);
        }

        IdempotencyKeyJpaEntity record = new IdempotencyKeyJpaEntity();
        record.setKey(key);
        record.setUserId(userId);
        record.setEndpoint(request.getRequestURI());
        record.setRequestHash(hash);
        record.setResponseBody(responseBody);
        record.setHttpStatus(status);
        repository.save(record);

        log.debug("Stored idempotency key {} for user {} endpoint {}", key, userId, request.getRequestURI());
    }

    private UUID currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails userDetails)) {
            return null;
        }
        try {
            return UUID.fromString(userDetails.getUsername());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String hashRequestBody(HttpServletRequest request) {
        try {
            byte[] body = request.getInputStream().readAllBytes();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(body));
        } catch (Exception e) {
            log.warn("Could not hash request body for idempotency check: {}", e.getMessage());
            return "";
        }
    }
}
```

> **Note:** Reading `request.getInputStream()` in the interceptor consumes the stream. The controller will no longer be able to deserialize the body. Wrap the request in a `ContentCachingRequestWrapper` in the `WebMvcConfig` filter to allow multiple reads.

### 5. Create or update `WebMvcConfig.java`

Register the interceptor and wrap requests/responses to allow body replay:

```java
package com.instagram.infrastructure.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.CommonsRequestLoggingFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.util.ContentCachingRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final IdempotencyInterceptor idempotencyInterceptor;

    public WebMvcConfig(IdempotencyInterceptor idempotencyInterceptor) {
        this.idempotencyInterceptor = idempotencyInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Only apply to POST endpoints — the idempotency check is a no-op if the header is absent
        registry.addInterceptor(idempotencyInterceptor)
                .addPathPatterns("/api/v1/posts", "/api/v1/messages");
    }

    /**
     * Wrap every request in a cacheable wrapper so the interceptor can read
     * the body without consuming the stream for the controller.
     */
    @Bean
    public FilterRegistrationBean<ContentCachingRequestFilter> requestCachingFilter() {
        FilterRegistrationBean<ContentCachingRequestFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new ContentCachingRequestFilter());
        bean.addUrlPatterns("/api/*");
        bean.setOrder(1);
        return bean;
    }

    /**
     * Wrap every response in a cacheable wrapper so the interceptor can read
     * the body in afterCompletion.
     */
    @Bean
    public FilterRegistrationBean<Filter> responseCachingFilter() {
        FilterRegistrationBean<Filter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response,
                                 FilterChain chain) throws IOException, ServletException {
                ContentCachingResponseWrapper wrappedResponse =
                        new ContentCachingResponseWrapper((HttpServletResponse) response);
                chain.doFilter(request, wrappedResponse);
                wrappedResponse.copyBodyToResponse(); // flush stored body to actual response
            }
        });
        bean.addUrlPatterns("/api/*");
        bean.setOrder(2);
        return bean;
    }
}
```

### 6. Add the test

Create `backend/src/test/java/com/instagram/adapter/in/web/IdempotencyIT.java`:

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class IdempotencyIT {

    // ... test setup with TestRestTemplate and two registered users

    @Test
    @DisplayName("Same Idempotency-Key twice → one DB row, second call returns cached response")
    void createPost_sameKey_returnsCachedResponse() {
        String idempotencyKey = UUID.randomUUID().toString();
        String body = /* valid create-post JSON */;

        // First call — creates the post
        ResponseEntity<String> first = restTemplate.exchange(
            "/api/v1/posts", HttpMethod.POST,
            entityWithKey(idempotencyKey, body, userAToken),
            String.class);
        assertThat(first.getStatusCodeValue()).isEqualTo(201);

        // Second call with same key — must return same response without creating a new post
        ResponseEntity<String> second = restTemplate.exchange(
            "/api/v1/posts", HttpMethod.POST,
            entityWithKey(idempotencyKey, body, userAToken),
            String.class);
        assertThat(second.getStatusCodeValue()).isEqualTo(201);
        assertThat(second.getBody()).isEqualTo(first.getBody());

        // Exactly one post in the database
        long postCount = postRepository.count();
        assertThat(postCount).isEqualTo(1L);
    }

    @Test
    @DisplayName("Same key with different body → 409 Conflict")
    void createPost_sameKeyDifferentBody_returns409() {
        String idempotencyKey = UUID.randomUUID().toString();

        restTemplate.exchange("/api/v1/posts", HttpMethod.POST,
            entityWithKey(idempotencyKey, bodyA, userAToken), String.class);

        ResponseEntity<String> conflict = restTemplate.exchange(
            "/api/v1/posts", HttpMethod.POST,
            entityWithKey(idempotencyKey, bodyB, userAToken), String.class);
        assertThat(conflict.getStatusCodeValue()).isEqualTo(409);
    }
}
```

---

## Checklist

- [ ] Accept an `Idempotency-Key` header on create-post and send-message endpoints
  - [ ] `IdempotencyInterceptor.preHandle` reads the header
  - [ ] Missing or malformed key passes through (not required, but recommended to add to API docs)
- [ ] Flyway migration for an `idempotency_key` table (key, request hash, stored response, status, created_at)
  - [ ] `V4__idempotency_keys.sql` created and applied on boot
- [ ] Add an interceptor/guard that records the key in the same transaction and short-circuits duplicates
  - [ ] `IdempotencyInterceptor` registered via `WebMvcConfig`
  - [ ] Request/response body caching filters registered
- [ ] Return the stored response on a repeated key; `409 Conflict` if the same key arrives with a different payload
  - [ ] Repeated key + same body → returns stored `responseBody` and `httpStatus`
  - [ ] Repeated key + different body → `409` with descriptive error message
- [ ] Add a test firing the same key twice and asserting one side-effect
  - [ ] `IdempotencyIT` — two-call test asserting single DB row
  - [ ] Conflict test asserting `409` on mismatched body

---

## How to Verify

**Two identical POST calls create one post:**

```powershell
$key   = [guid]::NewGuid().ToString()
$body  = '{"caption":"Test","mediaItems":[{"mediaUrl":"http://x/y","mediaType":"IMAGE","displayOrder":0}]}'
$hdrs  = @{ Authorization = "Bearer $token"; "Idempotency-Key" = $key }

$r1 = Invoke-RestMethod "http://localhost:8080/api/v1/posts" `
    -Method POST -ContentType "application/json" -Headers $hdrs -Body $body
$r2 = Invoke-RestMethod "http://localhost:8080/api/v1/posts" `
    -Method POST -ContentType "application/json" -Headers $hdrs -Body $body

$r1.data.id -eq $r2.data.id   # Expected: True (same post ID returned both times)
```

**Same key, different body → 409:**

```powershell
$key  = [guid]::NewGuid().ToString()
$hdrs = @{ Authorization = "Bearer $token"; "Idempotency-Key" = $key }

Invoke-RestMethod "http://localhost:8080/api/v1/posts" `
    -Method POST -ContentType "application/json" -Headers $hdrs `
    -Body '{"caption":"First",...}'

$r = Invoke-WebRequest "http://localhost:8080/api/v1/posts" `
    -Method POST -ContentType "application/json" -Headers $hdrs `
    -Body '{"caption":"Different",...}' -SkipHttpErrorCheck
Write-Host $r.StatusCode   # Expected: 409
```

---

## Notes / Gotchas

**Reading the request body in the interceptor requires `ContentCachingRequestWrapper`.**
Spring's default `HttpServletRequest` input stream can only be read once. If the interceptor reads it, the Jackson deserializer in the controller sees an empty body. The `ContentCachingRequestFilter` (Step 5) wraps the request so the body can be read multiple times. Confirm the filter's registration order puts it before the interceptor.

**Storing the full response body in the database has a cost.**
For large responses (e.g. a post with many media items) the stored JSON can be several kilobytes. This is acceptable for the post and message endpoints, which have modest response sizes. For future bulk-import endpoints, consider storing only the relevant fields (e.g. the created entity's ID) rather than the full response body.

**Idempotency keys should expire.**
The `idempotency_keys` table will grow indefinitely without a cleanup job. [TASK-10.48 (ShedLock scheduled jobs)](TASK-10.48-shedlock-scheduled-jobs.md) should include a job that deletes rows older than 24 hours (or whatever the client's retry window is).

**Scope idempotency keys to the user.**
The `findByKeyAndUserId` query scopes the lookup to the authenticated user. Two different users can use the same UUID as their idempotency key without conflict. Never look up a key without the user ID — otherwise User B could poison User A's key.

**Idempotency is not a substitute for transactions.**
If the backend crashes after writing the post to the database but before writing the idempotency key, the first request is committed but the key is not stored. The client will retry, and the interceptor will not find the key — so a second post is created. To fully eliminate this race condition, write the idempotency key in the same database transaction as the business operation using `@Transactional`. This is an advanced optimization; the above implementation handles the common case (network timeout after a successful response).

**Reference docs:**
- [Stripe — Idempotent Requests](https://stripe.com/docs/api/idempotent_requests)
- [IETF Draft — Idempotency-Key HTTP Header Field](https://datatracker.ietf.org/doc/draft-ietf-httpapi-idempotency-key-header/)
- [Spring — `ContentCachingRequestWrapper`](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/util/ContentCachingRequestWrapper.html)
