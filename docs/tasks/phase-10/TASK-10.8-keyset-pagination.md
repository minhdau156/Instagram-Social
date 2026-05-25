# TASK-10.8 — Keyset (cursor) pagination for list endpoints

## Overview

Several list endpoints in this application still use offset-based pagination: they tell Postgres `LIMIT 20 OFFSET 5000` to get "page 250 of 20 items". PostgreSQL must read and discard all 5000 rows before returning the 20 you asked for, and the cost grows linearly with page depth. Keyset pagination (also called cursor pagination) replaces the offset with a WHERE clause that seeks directly to the last seen item — it is O(page size) regardless of how deep the page is, and it never produces duplicate or skipped rows when items are inserted while the user is scrolling. The home feed already uses a `cursor` parameter (UUID of the last seen post) — this task standardises that pattern across comments, followers, and notifications, and makes the cursor opaque.

---

## Level

Core · Pairs with [TASK-10.7 index audit](TASK-10.7-database-index-audit.md)

---

## Why

`OFFSET 5000 LIMIT 20` makes Postgres read and discard 5000 rows every time, so deep pages get linearly slower and can skip or duplicate rows when data shifts underneath. If a new post is inserted at the top of the feed while a user is scrolling, every subsequent offset-based page shifts by one row — the user either sees a duplicate or skips a post silently. Keyset pagination seeks straight to the cursor — a known sort-key value — and stays O(page size). At page 1 or page 500 the query cost is identical.

---

## Prerequisites

- TASK-10.7 complete — the composite `(created_at DESC, id DESC)` index on `posts` (and similar indexes on `comments`, `follows`, `notifications`) must exist before keyset queries perform well.
- Understand the existing cursor pattern in `FeedJpaRepository.findHomeFeed()` — it uses `WHERE (:cursor IS NULL OR p.id < :cursor)` with a UUID cursor, which is a simplified keyset on `id` alone.
- Know how Base64 encoding/decoding works (the opaque cursor encoding mechanism).

**Concepts to skim:**
- Keyset pagination / seek method: filter with `WHERE (sort_col, id) < (cursor_val, cursor_id)` + `ORDER BY sort_col DESC, id DESC LIMIT size`. The cursor encodes the sort key of the last row returned.
- Opaque cursor: the cursor value given to the client is Base64-encoded so the client cannot parse or depend on its internal format. This lets you change the sort key later without breaking client integrations.
- `CursorPage<T>`: a generic response shape containing a list of items and a `nextCursor` string. Null means there is no next page.
- Composite index backing the sort key: for `ORDER BY created_at DESC, id DESC`, you need an index on `(created_at DESC, id DESC)`. Without it, Postgres must sort the entire result set in memory.

---

## Files to Create / Modify

```
backend/src/main/java/com/instagram/adapter/in/web/dto/response/CursorPageResponse.java   (new)
backend/src/main/java/com/instagram/infrastructure/util/CursorEncoder.java                (new)
backend/src/main/java/com/instagram/adapter/out/persistence/repository/CommentJpaRepository.java  (modify)
backend/src/main/java/com/instagram/adapter/out/persistence/repository/NotificationJpaRepository.java  (modify)
backend/src/main/java/com/instagram/adapter/out/persistence/repository/FollowJpaRepository.java        (modify)
backend/src/main/java/com/instagram/adapter/in/web/CommentController.java                  (modify)
backend/src/main/java/com/instagram/adapter/in/web/NotificationController.java            (modify)
backend/src/main/java/com/instagram/adapter/in/web/FollowController.java                  (modify)
frontend/src/hooks/useInfiniteScroll.ts                                                     (modify — if it uses page numbers)
```

---

## Step-by-Step

### 1. Create the CursorPageResponse record

Create `backend/src/main/java/com/instagram/adapter/in/web/dto/response/CursorPageResponse.java`:

```java
package com.instagram.adapter.in.web.dto.response;

import java.util.List;

/**
 * Standard paginated response shape for cursor (keyset) pagination.
 *
 * <p>{@code nextCursor} is an opaque Base64-encoded string that clients pass
 * back as the {@code cursor} query parameter to fetch the next page.
 * A {@code null} value means there is no next page.</p>
 */
public record CursorPageResponse<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore
) {
    public static <T> CursorPageResponse<T> of(List<T> items, String nextCursor) {
        return new CursorPageResponse<>(items, nextCursor, nextCursor != null);
    }
}
```

---

### 2. Create CursorEncoder — opaque Base64 cursor

Create `backend/src/main/java/com/instagram/infrastructure/util/CursorEncoder.java`:

```java
package com.instagram.infrastructure.util;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

/**
 * Encodes / decodes keyset pagination cursors as opaque Base64 strings.
 *
 * <p>Cursor format (before Base64): {@code "<iso8601_timestamp>|<uuid>"}
 * Example decoded value: {@code "2026-01-15T10:30:00Z|550e8400-e29b-41d4-a716-446655440000"}
 *
 * <p>Clients receive the Base64 form and must treat it as opaque.
 * This lets us change the internal format in a future migration without
 * breaking existing client code.</p>
 */
public final class CursorEncoder {

    private CursorEncoder() {}

    /** Encodes a (createdAt, id) pair into an opaque cursor string. */
    public static String encode(OffsetDateTime createdAt, UUID id) {
        String raw = createdAt.toString() + "|" + id.toString();
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** Decodes an opaque cursor into its component parts. */
    public static DecodedCursor decode(String cursor) {
        try {
            String raw = new String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            return new DecodedCursor(
                    OffsetDateTime.parse(parts[0]),
                    UUID.fromString(parts[1]));
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid cursor: " + cursor, e);
        }
    }

    public record DecodedCursor(OffsetDateTime createdAt, UUID id) {}
}
```

---

### 3. Update CommentJpaRepository with a keyset query

Open `backend/src/main/java/com/instagram/adapter/out/persistence/repository/CommentJpaRepository.java`.

Add a keyset-based method alongside the existing pageable method. The keyset query uses the composite `(created_at ASC, id ASC)` sort for comments (oldest first):

```java
/**
 * Keyset pagination for comments: returns comments on a post after the cursor position.
 * Use {@code cursorTs = null} and {@code cursorId = null} for the first page.
 */
@Query(value = """
        SELECT c.* FROM comments c
        WHERE c.post_id   = :postId
          AND NOT c.is_deleted
          AND c.parent_id IS NULL
          AND (
              :cursorTs IS NULL
              OR (c.created_at, c.id) > (:cursorTs::timestamptz, :cursorId::uuid)
          )
        ORDER BY c.created_at ASC, c.id ASC
        LIMIT :size
        """, nativeQuery = true)
List<CommentJpaEntity> findByPostIdKeysetAfter(
        @Param("postId") UUID postId,
        @Param("cursorTs") String cursorTs,    // ISO-8601 string or null
        @Param("cursorId") UUID cursorId,
        @Param("size") int size);
```

**Note:** Native queries cannot directly accept `OffsetDateTime` parameters through Spring Data in all Hibernate versions. Passing `cursorTs` as a `String` (ISO-8601) and casting it in the SQL with `::timestamptz` is a reliable workaround.

---

### 4. Update NotificationJpaRepository with a keyset query

Open `backend/src/main/java/com/instagram/adapter/out/persistence/repository/NotificationJpaRepository.java`.

Add a keyset method for notifications (most recent first):

```java
@Query(value = """
        SELECT n.* FROM notifications n
        WHERE n.recipient_id = :recipientId
          AND (
              :cursorTs IS NULL
              OR (n.created_at, n.id) < (:cursorTs::timestamptz, :cursorId::uuid)
          )
        ORDER BY n.created_at DESC, n.id DESC
        LIMIT :size
        """, nativeQuery = true)
List<NotificationJpaEntity> findByRecipientIdKeysetBefore(
        @Param("recipientId") UUID recipientId,
        @Param("cursorTs") String cursorTs,
        @Param("cursorId") UUID cursorId,
        @Param("size") int size);
```

---

### 5. Update FollowJpaRepository with a keyset query

Open `backend/src/main/java/com/instagram/adapter/out/persistence/repository/FollowJpaRepository.java`.

Add keyset methods for followers and following lists:

```java
@Query(value = """
        SELECT f.* FROM follows f
        WHERE f.following_id = :followingId
          AND f.is_approved = TRUE
          AND (:cursorTs IS NULL
               OR (f.created_at, f.follower_id) < (:cursorTs::timestamptz, :cursorId::uuid))
        ORDER BY f.created_at DESC, f.follower_id DESC
        LIMIT :size
        """, nativeQuery = true)
List<FollowJpaEntity> findFollowersKeysetBefore(
        @Param("followingId") UUID followingId,
        @Param("cursorTs") String cursorTs,
        @Param("cursorId") UUID cursorId,
        @Param("size") int size);
```

---

### 6. Update the CommentController to accept and return a cursor

Open `backend/src/main/java/com/instagram/adapter/in/web/CommentController.java`.

Replace the `page` / `size` parameters on the list-comments endpoint with `cursor` / `size`:

```java
@GetMapping("/{postId}/comments")
public ResponseEntity<ApiResponse<CursorPageResponse<CommentResponse>>> getComments(
        @PathVariable UUID postId,
        @RequestParam(required = false) String cursor,
        @RequestParam(defaultValue = "20") int size) {

    // Decode the opaque cursor
    CursorEncoder.DecodedCursor decoded = cursor != null
            ? CursorEncoder.decode(cursor)
            : null;

    List<CommentJpaEntity> entities = commentJpaRepository.findByPostIdKeysetAfter(
            postId,
            decoded != null ? decoded.createdAt().toString() : null,
            decoded != null ? decoded.id() : null,
            size + 1); // fetch one extra to detect hasMore

    boolean hasMore = entities.size() > size;
    List<CommentJpaEntity> page = hasMore ? entities.subList(0, size) : entities;

    String nextCursor = hasMore
            ? CursorEncoder.encode(
                    page.get(page.size() - 1).getCreatedAt(),
                    page.get(page.size() - 1).getId())
            : null;

    List<CommentResponse> items = page.stream()
            .map(CommentResponse::from)
            .toList();

    return ResponseEntity.ok(ApiResponse.ok(
            CursorPageResponse.of(items, nextCursor)));
}
```

The "fetch one extra" trick (fetching `size + 1` rows) is a standard way to determine `hasMore` without a separate `COUNT` query.

---

### 7. Update the frontend infinite-scroll hooks

Open the existing infinite-scroll hooks (e.g., `frontend/src/hooks/useConversations.ts`, `frontend/src/hooks/search/useSearch.ts`) that use `useInfiniteQuery`.

These hooks currently pass `allPages.length` as the next page index in `getNextPageParam`. Change them to pass `lastPage.nextCursor` instead:

```ts
// Before (offset-based):
getNextPageParam: (lastPage, allPages) => {
  return lastPage.posts.length === PAGE_SIZE ? allPages.length : undefined;
},

// After (cursor-based):
getNextPageParam: (lastPage) => {
  return lastPage.nextCursor ?? undefined;
},
```

In the query function, pass the cursor as a parameter:

```ts
queryFn: ({ pageParam = null }) =>
  feedApi.getHomeFeed({ cursor: pageParam as string | null, limit: PAGE_SIZE }),
```

---

## Checklist

- [ ] Standardize a `CursorPage<T>` response shape (items + `nextCursor`) across list endpoints
- [ ] Replace `OFFSET`-based queries with keyset `WHERE (created_at, id) < (:cursorTs, :cursorId) ORDER BY created_at DESC, id DESC LIMIT :size`
- [ ] Encode/decode the cursor opaquely (base64 of the sort key) so clients don't depend on its internals
- [ ] Ensure a composite index backs the sort key (coordinate with TASK-10.7)
- [ ] Update the frontend infinite-scroll hooks to pass `nextCursor` instead of an incrementing page index

---

## How to Verify

**Test that deep pages are fast:**

```powershell
# Get page 1
$page1 = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/posts/{postId}/comments?size=20" `
    -Headers @{Authorization="Bearer <token>"}
$cursor = $page1.data.nextCursor

# Measure page 2
Measure-Command {
    Invoke-RestMethod -Uri "http://localhost:8080/api/v1/posts/{postId}/comments?cursor=$cursor&size=20" `
        -Headers @{Authorization="Bearer <token>"}
}
```

**Passing result:** Page 2 takes a similar time to page 1 (within 2x). An offset-based query would be measurably slower for each deeper page.

**Verify the cursor is opaque:**

```powershell
# Decode the cursor manually
$cursor = "dGVzdA"  # replace with actual nextCursor value
[System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($cursor.PadRight(($cursor.Length + 3) -band -4, '=')))
```

Expected: the decoded value contains an ISO-8601 timestamp and a UUID separated by `|`.

**Verify no duplicate/skipped rows across pages:**

Collect all items from pages 1, 2, and 3 and confirm all IDs are unique:

```powershell
# Pseudo-test: call three pages and verify no duplicate IDs
$ids = @()
$cursor = $null
1..3 | ForEach-Object {
    $url = if ($cursor) {"...?cursor=$cursor&size=5"} else {"...?size=5"}
    $page = Invoke-RestMethod -Uri $url -Headers @{Authorization="Bearer <token>"}
    $ids += $page.data.items | ForEach-Object { $_.id }
    $cursor = $page.data.nextCursor
}
$uniqueCount = ($ids | Sort-Object -Unique).Count
Write-Host "Total items: $($ids.Count), Unique IDs: $uniqueCount"
# Should be equal — no duplicates
```

---

## Notes / Gotchas

**"The `(created_at, id) < (ts, id)` syntax doesn't work in my SQL client."**
Row comparison expressions like `(a, b) < (x, y)` are standard SQL and fully supported in PostgreSQL, but some ORMs (Hibernate JPQL in particular) do not support row comparisons. That is why the queries in this task use native SQL (`nativeQuery = true`). Do not try to translate them to JPQL.

**"NULL handling in the cursor IS NULL check."**
The condition `:cursorTs IS NULL OR (n.created_at, n.id) < (...)` must use `IS NULL` not `= NULL`. In SQL, `NULL = NULL` is `UNKNOWN`, not `TRUE`. Spring Data replaces a Java `null` parameter with a SQL `NULL`, and `NULL IS NULL` evaluates to `TRUE` correctly.

**"The home feed already has cursor pagination — do I need to change it?"**
The home feed uses a UUID-only cursor (`WHERE p.id < :cursor`), which works only because the feed query sorts by `created_at DESC` and the UUID v4 monotonicity is not reliable as a sort key on its own. Migrating it to the `(created_at, id)` composite cursor is the correct improvement, but it requires updating `FeedJpaRepository.findHomeFeed()`, `FeedController`, and the frontend `useHomeFeed` hook simultaneously. Coordinate with the feed team or do it as a follow-up.

**"The frontend uses `allPages.length` as the page index — where is this?"**
Search for `getNextPageParam` in `frontend/src/hooks/`:

```powershell
Select-String -Path frontend/src/hooks -Pattern "allPages.length" -Recurse
```

Each occurrence is an offset-based hook that needs to be converted to cursor-based.

**Cross-task references:**
- TASK-10.7 creates the `idx_posts_cursor ON posts (created_at DESC, id DESC)` index that backs the keyset `WHERE` clause. This task will perform poorly without that index on large datasets.
- TASK-10.3 (Redis caching) caches page 1 entirely, so keyset pagination matters most for pages 2+ (cache misses for deep pages).

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Why OFFSET pagination gets slow** — the cost of skipping rows at deep pages — https://use-the-index-luke.com/no-offset
- **Keyset (cursor) pagination** — page by a "seek" predicate instead of OFFSET — https://use-the-index-luke.com/sql/partial-results/fetch-next-page
- **Stable sort keys** — why the cursor column set must be unique & ordered — https://use-the-index-luke.com/sql/partial-results

### Official docs (code reference)
- **Spring Data JPA repositories** — https://docs.spring.io/spring-data/jpa/reference/
- **PostgreSQL LIMIT / ordering** — https://www.postgresql.org/docs/current/queries-limit.html
