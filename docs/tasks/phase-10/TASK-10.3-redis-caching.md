# TASK-10.3 — Redis caching for feed & profiles

## Overview

The home-feed and user-profile endpoints are the two most requested in the entire application. Every time a user opens the app or visits a profile, those expensive JOIN queries run against PostgreSQL — even if nothing has changed since the last request. This task wires Redis as an application-level cache: the first request goes to the database, the result is stored in Redis with a TTL, and every subsequent identical request is served directly from Redis without touching Postgres. Profile writes evict the cache so stale data is never served.

---

## Level

Core · Pairs with [TASK-10.1 baseline](TASK-10.1-performance-baseline-explain-analyze.md) / [TASK-10.2 pool tuning](TASK-10.2-hikaricp-pool-tuning.md)

---

## Why

The home-feed query joins the `follows` table against the `posts` table for every page-1 request. On a busy account this can scan thousands of rows even with good indexes. A Redis cache with a 60-second TTL means that if 100 users all request the same popular user's profile within one minute, Postgres runs the query once and Redis serves the other 99. Profile data changes rarely (a user edits their bio once a month, not once a second), so a 5-minute TTL is safe and the explicit cache eviction on profile update ensures no one ever sees stale data.

---

## Prerequisites

- Docker Compose stack includes a Redis service (`redis` image). If not present, add it to `docker-compose.yml` (covered in TASK-10.46).
- TASK-10.1 complete — you have a baseline query time to compare against after this task.
- Understand the difference between `@Cacheable` (read cache) and `@CacheEvict` (invalidate on write).
- Know where `FeedService.getHomeFeed()` and `UserService.getUserProfile()` live:
  - `backend/src/main/java/com/instagram/application/service/FeedService.java`
  - `backend/src/main/java/com/instagram/application/service/UserService.java`

**Concepts to skim:**
- Redis: an in-memory key/value store. Keys are strings; values can be strings, hashes, lists, etc. Spring Boot integrates via `spring-data-redis`.
- TTL (Time To Live): a Redis key is automatically deleted after its TTL expires. `60s` means the cached value is stale for at most 60 seconds.
- Cache eviction vs. expiry: eviction is explicit (your code says "delete this key now"); expiry is automatic (Redis deletes the key after the TTL).
- `@Cacheable`: Spring AOP intercepts the method call, checks Redis for the key, returns the cached value if present, or calls the method and stores the result if absent.
- `@CacheEvict`: Spring AOP deletes the named key from Redis when the annotated method is called.

---

## Files to Create / Modify

```
backend/pom.xml                                                                      (modify)
backend/src/main/resources/application.yml                                           (modify)
backend/src/main/resources/application-local.yml                                     (modify)
backend/src/main/java/com/instagram/infrastructure/config/RedisConfig.java           (new)
backend/src/main/java/com/instagram/application/service/FeedService.java             (modify)
backend/src/main/java/com/instagram/application/service/UserService.java             (modify)
backend/src/main/java/com/instagram/adapter/in/web/UserController.java               (modify)
```

---

## Step-by-Step

### 1. Add the Redis dependency to pom.xml

Open `backend/pom.xml` and add the following inside the `<dependencies>` block, after the existing Spring Boot starters:

```xml
<!-- Redis cache -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

Spring Boot's auto-configuration will detect this dependency and configure a `RedisConnectionFactory` bean automatically, pointing at `localhost:6379` by default.

---

### 2. Add Redis connection properties to application.yml

Open `backend/src/main/resources/application.yml` and add the following under the `spring:` key:

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      # password: ${REDIS_PASSWORD:}   # uncomment if your Redis requires auth

  cache:
    type: redis
    redis:
      time-to-live: 60000    # default TTL in ms (60 s); overridden per cache below
```

In `backend/src/main/resources/application-local.yml` you can confirm the local defaults are correct (no password, default port):

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

---

### 3. Create RedisConfig.java

Create a new configuration class at:

`backend/src/main/java/com/instagram/infrastructure/config/RedisConfig.java`

```java
package com.instagram.infrastructure.config;

import java.time.Duration;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    /**
     * General-purpose RedisTemplate using String keys and JSON values.
     * Used for manual cache operations (e.g. redis-cli KEYS '*' will show
     * human-readable keys like "feed:abc123:page1").
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    /**
     * RedisCacheManager with per-cache TTL configuration.
     *
     * Cache names and TTLs:
     *   "feed"    — 60 seconds  (home-feed page 1, invalidated by new posts)
     *   "profile" — 5 minutes   (user profile, evicted on profile update)
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer();

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(jsonSerializer))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> cacheConfigs = Map.of(
                "feed",    defaultConfig.entryTtl(Duration.ofSeconds(60)),
                "profile", defaultConfig.entryTtl(Duration.ofMinutes(5))
        );

        return RedisCacheManager.builder(factory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofSeconds(60)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
```

**Why `GenericJackson2JsonRedisSerializer`?**
It writes the Java class name into the JSON so Spring can deserialize back to the right type on a cache hit. Without it, you would need explicit type configuration or the cache would deserialize to `LinkedHashMap` instead of your domain class.

---

### 4. Wrap FeedService.getHomeFeed with a Redis cache

Open `backend/src/main/java/com/instagram/application/service/FeedService.java`.

Add the `@Cacheable` annotation on `getHomeFeed`. Cache only first-page requests (cursor is null), because deep-page cursors are unique per session and would pollute Redis with entries that are never reused.

```java
import org.springframework.cache.annotation.Cacheable;

// Inside FeedService class:

@Override
@Cacheable(
    value = "feed",
    key = "'feed:' + #query.userId() + ':page1'",
    condition = "#query.cursor() == null"   // only cache the first page
)
public GetHomeFeedUseCase.FeedPage getHomeFeed(GetHomeFeedUseCase.Query query) {
    // existing implementation unchanged
    List<Post> posts = feedRepository.getHomeFeed(
            query.userId(), query.cursor(), query.limit());
    // ... rest of method unchanged
}
```

**Key format:** `feed:{userId}:page1` — for example, `feed:550e8400-e29b-41d4-a716-446655440000:page1`.

The `condition` expression is evaluated before the method runs. If `cursor != null`, the annotation is skipped entirely and the method runs normally without caching.

---

### 5. Wrap UserService.getUserProfile with a Redis cache

Open `backend/src/main/java/com/instagram/application/service/UserService.java`.

Find the method implementing `GetUserProfileUseCase`. Add `@Cacheable`:

```java
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;

// Inside UserService class:

@Override
@Cacheable(value = "profile", key = "'profile:' + #command.username()")
public UserProfile getUserProfile(GetUserProfileUseCase.Command command) {
    // existing implementation unchanged
}
```

Now find the method implementing `UpdateProfileUseCase` and add cache eviction:

```java
@Override
@CacheEvict(value = "profile", key = "'profile:' + #command.username()")
public User updateProfile(UpdateProfileUseCase.Command command) {
    // existing implementation unchanged
}
```

This ensures that when a user edits their profile, the next `GET /api/v1/users/{username}` re-fetches from Postgres and stores a fresh entry in Redis.

---

### 6. Add Cache-Control header for public profiles

Open `backend/src/main/java/com/instagram/adapter/in/web/UserController.java`.

Find the handler for `GET /api/v1/users/{username}` (the profile endpoint). Add a `Cache-Control` header for public profiles:

```java
import org.springframework.http.CacheControl;
import java.util.concurrent.TimeUnit;

// Inside the getUserProfile handler:
@GetMapping("/{username}")
public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
        @PathVariable String username) {

    // existing delegation to use case...
    UserProfile profile = getUserProfileUseCase.getUserProfile(
            new GetUserProfileUseCase.Command(username));

    boolean isPublic = profile.user().getPrivacyLevel() == PrivacyLevel.PUBLIC;

    if (isPublic) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(300, TimeUnit.SECONDS).cachePublic())
                .body(ApiResponse.ok(UserProfileResponse.from(profile)));
    }
    return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(ApiResponse.ok(UserProfileResponse.from(profile)));
}
```

The `Cache-Control: public, max-age=300` header tells CDNs and browser caches they may cache the response for 5 minutes. Private profiles get `no-store` so they are never cached by intermediaries.

---

### 7. Start Redis and verify

Start Redis if it is not already running:

```powershell
docker compose up redis -d
```

Start the backend:

```powershell
cd backend
mvn spring-boot:run
```

Confirm Redis is reachable from the application startup log — you should NOT see:

```
Unable to connect to Redis
```

---

## Checklist

- [x] Add `spring-boot-starter-data-redis` to `pom.xml`
- [x] Create `RedisConfig.java` in `infrastructure/config/` — configure `RedisTemplate<String, Object>` with JSON serializer
- [x] Wrap `FeedService.getHomeFeed(cursor=null)` with a 60-second Redis cache (key: `feed:{userId}:page1`)
- [x] Wrap `UserService.getUserProfile(username)` with a 5-minute Redis cache (key: `profile:{username}`)
- [x] Evict profile cache on `UpdateProfileService` execution
- [x] Cache-Control header skipped — profile response includes `isFollowing` (viewer-specific); header would conflict with TanStack Query refetch logic

---

## How to Verify

**Step 1 — confirm a feed key is created in Redis:**

```powershell
# Make a feed request (replace <token> with a valid JWT)
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/feed" `
    -Headers @{Authorization="Bearer <token>"}

# Open Redis CLI
docker exec -it instagram-social-redis-1 redis-cli

# Inside redis-cli:
KEYS *
```

Expected output:
```
1) "feed:550e8400-e29b-41d4-a716-446655440000:page1"
```

**Step 2 — confirm TTL is set:**

```
TTL "feed:550e8400-e29b-41d4-a716-446655440000:page1"
```

Expected: a number between 1 and 60 (seconds remaining).

**Step 3 — confirm the second request is served from cache (no DB log):**

Enable Hibernate SQL logging temporarily in `application-local.yml`:

```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
```

Make the same feed request twice. The first request should log a SQL statement; the second (within 60 s) should not log any SQL because it is served from Redis.

**Step 4 — confirm profile eviction:**

```
# In redis-cli
KEYS *profile*
```

Make a `PUT /api/v1/users/me` profile update. Then re-check:

```
KEYS *profile*
```

The key should be gone (evicted) and will re-appear after the next `GET /api/v1/users/{username}`.

**Step 5 — verify Cache-Control header:**

```powershell
$response = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/users/testuser" `
    -Headers @{Authorization="Bearer <token>"}
$response.Headers["Cache-Control"]
```

Expected: `public, max-age=300`

---

## Notes / Gotchas

**"Bean of type `RedisCacheManager` already defined."**
Spring Boot auto-configures a `RedisCacheManager` if it finds Redis on the classpath. Because `RedisConfig.java` defines a custom `RedisCacheManager` bean, you must suppress the auto-configured one. The easiest way is to annotate `RedisConfig` with `@EnableCaching` (already done in the snippet above), which gives Spring Boot the signal to use your bean instead of the default.

**"Cannot serialize `UserProfile` to JSON."**
`GenericJackson2JsonRedisSerializer` requires that the objects being serialized have either a no-arg constructor (or Jackson annotations). Domain model classes in this project use hand-written builders and have no `@JsonCreator` annotations. You have two options:
1. Serialize the response DTO (e.g., `UserProfileResponse`) rather than the domain object — safer and smaller.
2. Add Jackson module configuration that handles records. Spring Boot 3.x includes `jackson-module-parameter-names` which handles records automatically.

**"Redis is not running — the app crashes on startup."**
The `spring-data-redis` auto-configuration attempts to connect on startup. If Redis is down, the app will fail. To make Redis optional (fall back to no caching), add `spring.cache.type=none` in a profile that runs without Redis. For local dev, just ensure `docker compose up redis` runs first.

**"Cache key collisions between users."**
The key format `feed:{userId}:page1` includes the user's UUID, so two different users' caches will never collide. Ensure the `key` SpEL expression in `@Cacheable` always includes the user ID for user-scoped data.

**Official Spring Cache documentation:**
https://docs.spring.io/spring-framework/reference/integration/cache.html

**Cross-task references:**
- Run TASK-10.1's `EXPLAIN ANALYZE` again after this task and compare execution times — the second request should show near-zero Postgres time.
- TASK-10.7 (index audit) reduces the Postgres time for the first (cache-miss) request.
- TASK-10.2 (HikariCP tuning) reduces the connection-wait time on that first request.

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Spring Cache abstraction** — how `@Cacheable` / `@CacheEvict` intercept method calls — https://docs.spring.io/spring-framework/reference/integration/cache.html
- **Caching, step by step** — the official getting-started walkthrough — https://spring.io/guides/gs/caching/
- **Redis & key expiry (TTL)** — in-memory key/value store and automatic expiry — https://redis.io/docs/
- **HTTP Cache-Control** — how browsers/CDNs cache responses — https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Cache-Control

### Official docs (code reference)
- **Spring Data Redis** — https://docs.spring.io/spring-data/redis/reference/
- **Spring Boot + Redis cache (Baeldung)** — https://www.baeldung.com/spring-boot-redis-cache
