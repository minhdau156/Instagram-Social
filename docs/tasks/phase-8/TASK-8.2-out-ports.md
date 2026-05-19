# TASK-8.2 — Out-Ports

## Overview

Define two out-port interfaces for the search domain: one for executing search queries against the database, and one for managing per-user search history. These live in `domain/port/out/` and are implemented by persistence adapters in TASK-8.5.

The port interfaces are clean domain abstractions — callers are unaware of the underlying query mechanism used by the persistence adapter.

## Requirements

- Pure Java interfaces — no Spring, JPA, or Lombok imports.
- Method signatures use domain types only (`User`, `Hashtag`, `Post`, `SearchHistory`) — never JPA entities.
- `Pageable` from `org.springframework.data.domain` is an accepted pragmatic import (same precedent as `NotificationRepository`).

## File Locations

```
backend/src/main/java/com/instagram/domain/port/out/SearchRepository.java
backend/src/main/java/com/instagram/domain/port/out/SearchHistoryRepository.java
```

---

## Checklist

### `SearchRepository.java`

- [x] `List<User> searchUsers(String query, Pageable pageable)`
  - Returns users whose `username` or `full_name` matches the query.
  - **Implementation strategy (TASK-8.5):** `ILIKE '%query%'` on both `username` and `full_name`. Results ordered by `follower_count` descending.

- [x] `List<Hashtag> searchHashtags(String query, Pageable pageable)`
  - Returns hashtags whose `name` starts with or is similar to the query (prefix match preferred). Results ordered by `post_count` descending.
  - **Implementation strategy (TASK-8.5):** prefix `ILIKE 'query%'` on `name`.

- [x] `List<Post> searchPosts(String query, Pageable pageable)`
  - Returns non-deleted posts whose `caption` contains the query.
  - **Implementation strategy (TASK-8.5):** `ILIKE '%query%'` on `caption`, ordered by `created_at` descending.

- [x] `List<Post> findPostsByHashtag(String hashtagName, Pageable pageable)`
  - Returns non-deleted posts linked to the given hashtag via the `post_hashtags` join table. Looks up the hashtag by `name` (case-insensitive via `CITEXT`), then joins to posts. Results ordered by `created_at` descending.

### `SearchHistoryRepository.java`

- [x] `SearchHistory save(SearchHistory searchHistory)`
  - Persists a new search history entry. Returns the saved instance (with a generated `id`).

- [x] `List<SearchHistory> findByUserIdOrderBySearchedAtDesc(UUID userId, Pageable pageable)`
  - Returns the most recent search entries for a user, newest first.

- [x] `void deleteByUserId(UUID userId)`
  - Deletes all search history entries for a user (used when the user clicks "Clear all").

## Notes

- `searchUsers` returns `List<User>` from `domain/model/User.java` — the existing Phase 1 domain model. Do not introduce a new type.
- `searchHashtags` returns `List<Hashtag>` from `domain/model/Hashtag.java` — the existing Phase 2 domain model.
- `searchPosts` returns `List<Post>` from `domain/model/Post.java` — the existing Phase 2 domain model.
- `findPostsByHashtag` is separated from `searchPosts` because it uses a join-table query, not a text search query. Browse-by-hashtag is not a relevance-ranked search.
- Do NOT add a `searchType` filter parameter to any of these methods. The service layer calls the appropriate method based on the `type` query parameter received from the controller.
- The port interfaces are intentionally unaware of any PostgreSQL-specific type. The database strategy is an implementation detail of the persistence adapter in TASK-8.5.
