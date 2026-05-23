# TASK-10.4 — N+1 query review

## Overview

The N+1 problem is one of the most common performance pitfalls in Hibernate-based applications. When you load a list of posts and each post entity has a lazily-loaded `@OneToMany` association (e.g., post media), Hibernate fires one SQL query for the list itself and then one more query for every single item in the list to fetch its associations — 1 + N queries total. Load 20 posts and you silently fire 21 queries. Load 100 posts and you fire 101. This task shows you how to detect N+1 with Hibernate statistics and fix it with `JOIN FETCH` or `@EntityGraph`.

---

## Level

Core · Pairs with [TASK-10.1 baseline](TASK-10.1-performance-baseline-explain-analyze.md) / [TASK-10.7 index audit](TASK-10.7-database-index-audit.md)

---

## Why

When a lazy association is read inside a loop, Hibernate fires one extra query per row — the "N+1 problem". One feed request that returns 20 posts but also accesses `post.getMediaItems()` inside the mapping loop will fire 21 queries against Postgres. With a pool size of 10 and 50 concurrent users, this amplifies to 1,050 queries instead of 50. Hibernate statistics make this visible in the logs so you can fix it with a single `JOIN FETCH` clause before it becomes a production problem.

---

## Prerequisites

- Basic understanding of JPA `FetchType.LAZY` vs. `FetchType.EAGER`.
- Know where the feed adapter lives: `FeedJpaQueryAdapter.java` and `PostPersistenceAdapter.java`.
- Know where the JPA entities are: `backend/src/main/java/com/instagram/adapter/out/persistence/entity/`.
- Know that `PostMediaJpaEntity` has a `@ManyToOne(fetch = FetchType.LAZY)` to `PostJpaEntity`.

**Concepts to skim:**
- `FetchType.LAZY`: Hibernate does not load the association when it loads the parent entity. It fires a second query only if/when your code actually accesses the association.
- `FetchType.EAGER`: Hibernate always loads the association in the same query as the parent. Safe for one-to-one but dangerous for one-to-many (can produce cartesian products).
- `JOIN FETCH`: a JPQL/HQL keyword that tells Hibernate to load the association in the same SQL query using a JOIN, avoiding the N+1.
- `@EntityGraph`: a JPA annotation alternative to `JOIN FETCH` that lets you declare which associations to eagerly load for a specific repository method.
- Hibernate statistics: a Hibernate feature you can enable in configuration that logs the number of queries, cache hits, etc. per session.

---

## Files to Create / Modify

```
backend/src/main/resources/application-local.yml                                      (modify)
backend/src/main/java/com/instagram/adapter/out/persistence/repository/PostMediaJpaRepository.java  (modify)
backend/src/main/java/com/instagram/adapter/out/persistence/FeedJpaQueryAdapter.java  (modify — if N+1 exists there)
```

---

## Step-by-Step

### 1. Enable Hibernate statistics and SQL logging

Open `backend/src/main/resources/application-local.yml` and add:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        generate_statistics: true   # logs query counts per session
    show-sql: false                  # keep SQL off; statistics give the count

logging:
  level:
    org.hibernate.stat: DEBUG        # prints the statistics summary
    org.hibernate.SQL: DEBUG         # set to DEBUG only when you want to see SQL
```

With `generate_statistics: true` and the `org.hibernate.stat` logger set to `DEBUG`, Hibernate will print a summary after each session closes. It looks like this:

```
Session Metrics {
    134920 nanoseconds spent acquiring 1 JDBC connections;
    0 nanoseconds spent releasing 0 JDBC connections;
    4530120 nanoseconds spent preparing 21 JDBC statements;   <-- 21 queries!
    ...
    21 statements executed
}
```

**21 statements for a 20-post feed response** is the N+1 signature.

---

### 2. Reproduce the problem

Start the backend and call the feed endpoint:

```powershell
# Replace <token> with a valid JWT
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/feed" `
    -Headers @{Authorization="Bearer <token>"}
```

Watch the backend logs. Look for `Session Metrics` or count the SQL statements in the log (if `show-sql: true`).

**Where to look in the code:**
The feed mapping in `FeedJpaQueryAdapter.toDomain()` reads `entity.getUser().getId()`. The `PostJpaEntity` has:

```java
@ManyToOne(fetch = FetchType.LAZY, optional = false)
@JoinColumn(name = "user_id", nullable = false)
private UserJpaEntity user;
```

This is a lazy `@ManyToOne`. When the native query in `FeedJpaRepository.findHomeFeed()` returns `PostJpaEntity` instances, the `user` field is a Hibernate proxy. Calling `entity.getUser().getId()` triggers a second query per post to load the `UserJpaEntity`. That is the N+1.

Additionally, if any code accesses `PostMedia` for each post in a loop (e.g., `FeedService.getHomeFeed()` calls `postMediaRepository.findByPostIds(postIds)` — look at how that is implemented), that is a separate batch-fetch path.

---

### 3. Fix #1 — avoid the N+1 on the user association in the feed query

The native query in `FeedJpaRepository.findHomeFeed()` returns `PostJpaEntity` objects but does not JOIN the user. When `toDomain()` calls `entity.getUser().getId()`, Hibernate fires a second query.

**Fix:** the `user_id` column is already on the `posts` table — you do not need to load the full `UserJpaEntity` to get the UUID. Change `FeedJpaQueryAdapter.toDomain()` to avoid triggering the lazy load:

The current mapping in `FeedJpaQueryAdapter` calls:
```java
.userId(entity.getUser().getId())
```

Since `user_id` is available directly on `PostJpaEntity` as a foreign key column, the cleanest fix is to add a `userId` column mapping to `PostJpaEntity` (read-only, insertable=false, updatable=false) so you can access it without a proxy load:

Open `backend/src/main/java/com/instagram/adapter/out/persistence/entity/PostJpaEntity.java` and add a read-only column for the raw FK:

```java
@Column(name = "user_id", insertable = false, updatable = false)
private UUID userId;
```

Then update `FeedJpaQueryAdapter.toDomain()` to use `entity.getUserId()` instead of `entity.getUser().getId()`:

```java
private Post toDomain(PostJpaEntity entity) {
    return Post.builder()
            .id(entity.getId())
            .userId(entity.getUserId())   // no proxy load
            .caption(entity.getCaption())
            // ... rest unchanged
            .build();
}
```

This eliminates one query per post for the feed path.

---

### 4. Fix #2 — batch-fetch PostMedia efficiently

`FeedService.getHomeFeed()` already does this correctly — it calls `postMediaRepository.findByPostIds(postIds)` with all post IDs at once, loading all media in a single IN-clause query instead of one query per post. Verify this is how `PostMediaRepository` is implemented:

```powershell
Select-String -Path "backend/src/main/java/com/instagram/adapter/out/persistence/repository/PostMediaJpaRepository.java" -Pattern "findByPostIdIn"
```

If `findByPostIds` uses a `findByPostIdIn` Spring Data method, this is already a single-query batch fetch. Good — no fix needed here.

If instead the code calls `postMediaRepository.findByPostId(singleId)` inside a loop, that is N+1 and must be replaced with a single `findByPostIdIn(Collection<UUID>)` call.

---

### 5. Verify the fix with Hibernate statistics

Restart the backend with `generate_statistics: true` still enabled. Call the feed endpoint again and count the queries in the log.

**Before fix:** `21 statements executed` (for 20 posts).

**After fix:** `2–3 statements executed` (one for follows+posts JOIN, one for media batch fetch, one for likes check).

The exact number depends on how many separate queries `FeedService` makes, but it should be a small constant number regardless of how many posts come back.

---

### 6. Write an integration test with statistics

Add a test to verify the query count stays bounded. In the existing test suite, find the feed integration test or add one:

```java
// backend/src/test/java/com/instagram/adapter/FeedControllerIT.java (or similar)

@Test
void feedEndpoint_shouldNotProduceNPlusOneQueries() {
    // Arrange — create 10 posts by followed users

    // Enable Hibernate statistics programmatically
    SessionFactory sessionFactory = entityManager
            .getEntityManagerFactory()
            .unwrap(SessionFactory.class);
    Statistics stats = sessionFactory.getStatistics();
    stats.setStatisticsEnabled(true);
    stats.clear();

    // Act — call the use case (not the HTTP endpoint, to avoid auth setup)
    feedService.getHomeFeed(new GetHomeFeedUseCase.Query(followerId, null, 20));

    // Assert — query count is bounded (not 1 + number of posts)
    long queryCount = stats.getQueryExecutionCount()
                     + stats.getPrepareStatementCount();
    assertThat(queryCount).isLessThan(5); // adjust to your actual count
}
```

---

## Checklist

- [ ] Audit all `@OneToMany` and `@ManyToOne` relationships — replace `FetchType.EAGER` with `FetchType.LAZY` where missing
- [ ] Add `@EntityGraph` or `JOIN FETCH` to queries that need multiple associations in one call (e.g., `PostJpaRepository` loading `PostMedia` in the feed)
- [ ] Run integration tests with Hibernate statistics enabled to verify no N+1 on feed endpoint

---

## How to Verify

**Quick log check:**

Enable `logging.level.org.hibernate.stat: DEBUG`, call `GET /api/v1/feed`, and confirm the statistics summary shows a small, constant number of statements.

**Passing result:** The `Session Metrics` block shows 2–4 JDBC statements for a 20-post feed — not 21.

**SQL-level check (optional):**

Set `spring.jpa.show-sql: true` and count the `SELECT` lines in the log for one feed request. The count should not grow linearly with the number of posts returned.

**Automated test:**

```powershell
cd backend
mvn test -Dtest=FeedControllerIT -pl backend
```

The test should pass with a query count assertion. If it fails with `queryCount=21`, the N+1 is still present.

---

## Notes / Gotchas

**"I added `FetchType.EAGER` to fix it — now the tests are slow."**
`EAGER` on a `@OneToMany` causes Hibernate to always load the collection, which produces a JOIN that multiplies rows (a post with 5 media items appears 5 times in the result set — Hibernate deduplicates, but the extra rows cost network and memory). Use `JOIN FETCH` in specific query methods instead of `EAGER` at the entity level.

**"The `@Column(insertable=false, updatable=false)` approach — is it safe?"**
Yes. Hibernate will read the `user_id` column value into `userId` but will never attempt to write it through this field (the `@ManyToOne` association still manages the write). This is a common pattern when you need the FK UUID without loading the full associated entity.

**"The statistics log shows 0 queries — that's wrong."**
`generate_statistics` only counts Hibernate-managed queries (JPQL, Criteria, `@Query`). Native SQL queries (the ones in `FeedJpaRepository` using `nativeQuery = true`) may be counted under `PreparedStatement` rather than `QueryExecutionCount`. Use both counters when evaluating the total.

**"I can't find `LazyInitializationException`."**
N+1 does not always throw an exception — it just silently fires extra queries inside an open Hibernate session. `LazyInitializationException` only happens when you try to access a lazy association *after* the session has closed. Enable SQL logging to see the extra queries.

**Official Hibernate performance guide:**
https://docs.jboss.org/hibernate/orm/6.4/userguide/html_single/Hibernate_User_Guide.html#fetching

**Cross-task references:**
- TASK-10.7 adds indexes that speed up the queries that remain after the N+1 is fixed.
- TASK-10.3 caches the feed result so the N+1 only fires on cache misses.
