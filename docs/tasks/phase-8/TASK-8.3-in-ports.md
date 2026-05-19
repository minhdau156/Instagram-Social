# TASK-8.3 — In-Ports (Use-Case Interfaces)

## Overview

Create six use-case interfaces (in-ports) for the search feature. One interface per use case, one method per interface. Each has an inner `Query` or `Command` record. Follow the exact same structure as the notification in-ports (TASK-7.5) and the `search` sub-package pattern.

## Requirements

- Lives in `domain/port/in/search/` — mirrors the `notification` and `messaging` sub-package pattern.
- Pure Java — no framework imports.
- One file per use case.

## File Locations

```
backend/src/main/java/com/instagram/domain/port/in/search/SearchUsersUseCase.java
backend/src/main/java/com/instagram/domain/port/in/search/SearchHashtagsUseCase.java
backend/src/main/java/com/instagram/domain/port/in/search/SearchPostsUseCase.java
backend/src/main/java/com/instagram/domain/port/in/search/GetPostsByHashtagUseCase.java
backend/src/main/java/com/instagram/domain/port/in/search/GetSearchHistoryUseCase.java
backend/src/main/java/com/instagram/domain/port/in/search/ClearSearchHistoryUseCase.java
```

---

## Checklist

### `SearchUsersUseCase.java`

- [ ] Method: `List<User> searchUsers(Query query)`
- [ ] Inner record: `Query(String q, UUID currentUserId, int page, int size)`
  - `currentUserId` is included so the service can later exclude the caller from results or annotate results with "is following" flags.

### `SearchHashtagsUseCase.java`

- [ ] Method: `List<Hashtag> searchHashtags(Query query)`
- [ ] Inner record: `Query(String q, int page, int size)`
  - No `currentUserId` needed — hashtag results are not personalised.

### `SearchPostsUseCase.java`

- [ ] Method: `List<Post> searchPosts(Query query)`
- [ ] Inner record: `Query(String q, UUID currentUserId, int page, int size)`
  - `currentUserId` included to allow future filtering (e.g., hide posts from blocked users).

### `GetPostsByHashtagUseCase.java`

- [ ] Method: `List<Post> getPostsByHashtag(Query query)`
- [ ] Inner record: `Query(String hashtagName, UUID currentUserId, int page, int size)`
  - Uses the hashtag `name` (e.g., `"travel"` — no leading `#`) as the lookup key, matching the `CITEXT` column in the schema.

### `GetSearchHistoryUseCase.java`

- [ ] Method: `List<SearchHistory> getSearchHistory(Query query)`
- [ ] Inner record: `Query(UUID userId, int limit)`
  - `limit` caps the number of returned entries (default 10 in the controller). This avoids fetching hundreds of old entries.

### `ClearSearchHistoryUseCase.java`

- [ ] Method: `void clearSearchHistory(Command command)`
- [ ] Inner record: `Command(UUID userId)`

## Notes

- All six use cases are implemented by the single `SearchService` class in TASK-8.4.
- The `GetSearchHistoryUseCase` uses `limit` rather than `page` + `size` — history is always fetched as a flat recent list, not paginated.
- Return types use the existing domain models: `User` (Phase 1), `Hashtag` (Phase 2), `Post` (Phase 2), `SearchHistory` (TASK-8.1).
