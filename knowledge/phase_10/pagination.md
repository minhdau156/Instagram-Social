# Pagination: Cursor, Keyset, Offset/Limit

## What is it
Pagination splits a large result set into smaller pages so a client doesn't have to load (and a server doesn't have to send) millions of rows at once. There are three common strategies:

- **Offset/Limit**: `SELECT * FROM posts ORDER BY id LIMIT 20 OFFSET 40;` — the database counts past `OFFSET` rows, discards them, then returns the next `LIMIT` rows. Page number is just `offset = page * pageSize`.
- **Keyset (a.k.a. seek method)**: instead of counting rows to skip, you remember the last row's sort key(s) from the previous page and query "the next rows after that value": `SELECT * FROM posts WHERE created_at < :lastSeenCreatedAt ORDER BY created_at DESC LIMIT 20;`
- **Cursor-based**: an API-level wrapper around keyset pagination. The "cursor" is an opaque token (often a base64-encoded encoding of the keyset column values, e.g. `created_at` + `id`) that the client passes back to get the next page, without needing to know or construct the underlying WHERE clause itself.

In practice: cursor pagination is keyset pagination exposed through an opaque token instead of raw column values.

## Why use it
- **Offset/Limit**: simple to implement, lets users jump to an arbitrary page number (e.g., "go to page 5"), works naturally with UI page-number controls.
- **Keyset/Cursor**: avoids the "counting/skipping" cost — `OFFSET 100000` still forces the database to scan and discard 100,000 rows before returning results, which gets slower as the offset grows (O(offset + limit) vs keyset's O(limit)). Keyset also avoids a correctness bug offset has: if a row is inserted/deleted while paging, offset pagination can skip or duplicate rows on the next page; keyset doesn't, since it always seeks relative to a stable value, not a position count.

## How can use it
**Offset/Limit (Spring Data JPA)**:
```java
Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by("createdAt").descending());
Page<Post> posts = postRepository.findAll(pageable);
```

**Keyset/Cursor (Spring Data JPA)**:
```java
@Query("SELECT p FROM Post p WHERE p.createdAt < :cursorCreatedAt " +
       "OR (p.createdAt = :cursorCreatedAt AND p.id < :cursorId) " +
       "ORDER BY p.createdAt DESC, p.id DESC")
List<Post> findNextPage(@Param("cursorCreatedAt") Instant cursorCreatedAt,
                         @Param("cursorId") Long cursorId,
                         Pageable pageable); // pageable only used for LIMIT here
```
The two-column comparison (`createdAt` + `id` as tiebreaker) is required because `createdAt` alone isn't guaranteed unique — without the tiebreaker you can skip or repeat rows that share the same timestamp.

To make it a true "cursor" for an API response, encode the tiebreaker values into an opaque string:
```java
String cursor = Base64.getEncoder().encodeToString((createdAt + "_" + id).getBytes());
```
The client sends this cursor back as-is; the server decodes it to reconstruct `cursorCreatedAt`/`cursorId` for the next query. This also hides internal column names/values from API consumers.

Always back the sort columns with a composite index: `CREATE INDEX idx_posts_created_id ON posts(created_at DESC, id DESC);` — otherwise keyset pagination degrades to a sequential scan, losing its main advantage.

## When use it in reallife
- **Offset/Limit**: admin dashboards, small tables, or anywhere users need "jump to page N" (e.g., search results with page numbers 1, 2, 3...). Acceptable when total row counts stay small (thousands, not millions) or the table is rarely deep-paged.
- **Cursor/Keyset**: infinite-scroll feeds (Instagram/Twitter-style timelines), APIs consumed by mobile apps ("load more" pattern), any large or fast-growing table (posts, comments, notifications, logs) where users rarely jump to a specific page but constantly scroll forward, and where consistent results under concurrent inserts/deletes matter (a live feed where new posts keep arriving).
