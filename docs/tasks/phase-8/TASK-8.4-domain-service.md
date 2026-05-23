# TASK-8.4 — Application Service: SearchService

## Overview

Create `SearchService` — the single service that implements all six search use-case in-ports. It delegates DB queries to `SearchRepository`, and asynchronously persists search terms to `SearchHistoryRepository`. Because it requires `@Async` (a Spring annotation) it lives in `application/service/`, following the same convention as `FeedService` and `NotificationService`.

## Requirements

- Lives in `application/service/` — NOT `domain/service/`. Reason: `@Async` is a Spring annotation and cannot be used in the pure-Java domain layer.
- `@Service`, constructor injection only, all dependencies `final`.
- Implements all six use-case interfaces from TASK-8.3.

## File Location

```
backend/src/main/java/com/instagram/application/service/SearchService.java
```

---

## Checklist

### Dependencies to inject (constructor)

- [x] `SearchRepository` — for executing user, hashtag, post, and hashtag-post queries.
- [x] `SearchHistoryRepository` — for saving and retrieving per-user search history.

### `searchUsers`

- [x] Validate that `query.q()` is not blank. If it is blank or null, return an empty list immediately without hitting the database.
- [x] Call `searchRepository.searchUsers(query.q(), PageRequest.of(query.page(), query.size()))`.
- [x] After the DB call returns, asynchronously save the search term by calling `saveHistoryAsync(query.currentUserId(), query.q())` (see below).
- [x] Return the `List<User>`.

### `searchHashtags`

- [x] Validate `query.q()` is not blank; return empty list if so.
- [x] Call `searchRepository.searchHashtags(query.q(), PageRequest.of(query.page(), query.size()))`.
- [x] Asynchronously save history — but only for hashtag searches initiated by an authenticated user. Since `SearchHashtagsUseCase.Query` does not carry `currentUserId`, skip the history save for now (hashtag searches are treated as anonymous for history purposes).
- [x] Return the `List<Hashtag>`.

### `searchPosts`

- [x] Validate `query.q()` is not blank; return empty list if so.
- [x] Call `searchRepository.searchPosts(query.q(), PageRequest.of(query.page(), query.size()))`.
- [x] Asynchronously save history via `saveHistoryAsync(query.currentUserId(), query.q())`.
- [x] Return the `List<Post>`.

### `getPostsByHashtag`

- [x] Validate `query.hashtagName()` is not blank; return empty list if so.
- [x] Call `searchRepository.findPostsByHashtag(query.hashtagName(), PageRequest.of(query.page(), query.size()))`.
- [x] No history save — browsing a hashtag page is not a search action.
- [x] Return the `List<Post>`.

### `getSearchHistory`

- [x] Call `searchHistoryRepository.findByUserIdOrderBySearchedAtDesc(query.userId(), PageRequest.ofSize(query.limit()))`.
- [x] Return the list as-is.

### `clearSearchHistory`

- [x] Call `searchHistoryRepository.deleteByUserId(command.userId())`.

### `saveHistoryAsync` (private helper)

- [x] Annotate with `@Async` — Spring will run this method in a separate thread pool so the HTTP response is not delayed by the INSERT.
- [x] Build a `SearchHistory` using the Builder: set `userId`, `query`, `searchedAt = OffsetDateTime.now()`. Leave `id` null — the persistence adapter generates it.
- [x] Call `searchHistoryRepository.save(searchHistory)`.
- [x] This method returns `void` (or `CompletableFuture<Void>` if the `@Async` configuration requires a return type — check `AsyncConfig` or `@EnableAsync` setup in the project).

## Notes

- `@Async` requires `@EnableAsync` somewhere in the application configuration. Check if it is already present (it was likely added for `NotificationEventHandler` in TASK-7.7). If not, add `@EnableAsync` to an existing `@Configuration` class in `infrastructure/config/`.
- Do NOT annotate individual `searchUsers`/`searchPosts` methods with `@Transactional` — these are read-only and the JPA adapter handles its own transaction boundaries. If `@Transactional(readOnly = true)` is desired, it is safe to add, but not required.
- The `clearSearchHistory` method can be annotated `@Transactional` if `SearchHistoryRepository.deleteByUserId` issues a bulk DELETE — this ensures the delete is atomic.
- The blank-query early return prevents empty-string database queries that would return the entire table.
