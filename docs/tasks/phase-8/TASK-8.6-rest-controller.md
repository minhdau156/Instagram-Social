# TASK-8.6 — REST Controller: SearchController

## Overview

Create `SearchController` exposing all search HTTP endpoints. Delegates entirely to use-case interfaces — no business logic in the controller. The controller resolves the current user's ID from the security context for endpoints that need it.

## Requirements

- `@RestController @RequestMapping("/api/v1")`.
- `@Tag(name = "Search")` for Swagger grouping.
- All endpoints require authentication (covered by the existing `SecurityConfig`).
- Return `ResponseEntity<ApiResponse<T>>` — consistent with all other controllers.

## File Location

```
backend/src/main/java/com/instagram/adapter/in/web/SearchController.java
```

---

## Checklist

### Injected dependencies (constructor)

- [ ] `SearchUsersUseCase`
- [ ] `SearchHashtagsUseCase`
- [ ] `SearchPostsUseCase`
- [ ] `GetPostsByHashtagUseCase`
- [ ] `GetSearchHistoryUseCase`
- [ ] `ClearSearchHistoryUseCase`

### Endpoints

#### `GET /api/v1/search`

- [ ] Query params:
  - `@RequestParam String q` — the search term; required.
  - `@RequestParam String type` — which entity to search: `"users"`, `"hashtags"`, or `"posts"`.
  - `@RequestParam(defaultValue = "0") int page`
  - `@RequestParam(defaultValue = "20") int size`
- [ ] Resolve `currentUserId` from the security context (same pattern used in other controllers — call the existing `currentUserId()` helper method).
- [ ] Dispatch based on `type`:
  - `"users"` → call `searchUsersUseCase.searchUsers(new Query(q, currentUserId, page, size))` → map each `User` to `UserSearchResponse.from(user)` → return `200 OK`.
  - `"hashtags"` → call `searchHashtagsUseCase.searchHashtags(new Query(q, page, size))` → map each `Hashtag` to `HashtagSearchResponse.from(hashtag)` → return `200 OK`.
  - `"posts"` → call `searchPostsUseCase.searchPosts(new Query(q, currentUserId, page, size))` → map each `Post` to `PostSearchResponse.from(post)` → return `200 OK`.
  - Unknown `type` → return `400 BAD REQUEST` with an error message `"Invalid search type: {type}. Use 'users', 'hashtags', or 'posts'."`.
- [ ] Return type for this endpoint is `ResponseEntity<ApiResponse<?>>` since the body changes based on `type`. Alternatively, define a wrapper `SearchResultResponse` DTO (see TASK-8.7).

#### `GET /api/v1/search/history`

- [ ] No query params.
- [ ] Resolve `currentUserId` from security context.
- [ ] Call `getSearchHistoryUseCase.getSearchHistory(new Query(currentUserId, 10))` — limit 10 most recent entries.
- [ ] Map each `SearchHistory` to `SearchHistoryResponse.from(entry)`.
- [ ] Return `200 OK` with the list.

#### `DELETE /api/v1/search/history`

- [ ] No request body.
- [ ] Resolve `currentUserId`.
- [ ] Call `clearSearchHistoryUseCase.clearSearchHistory(new Command(currentUserId))`.
- [ ] Return `204 NO CONTENT`.

#### `GET /api/v1/hashtags/{name}/posts`

- [ ] Path variable: `@PathVariable String name` — the hashtag name without the `#` prefix.
- [ ] Query params: `@RequestParam(defaultValue = "0") int page`, `@RequestParam(defaultValue = "20") int size`.
- [ ] Resolve `currentUserId`.
- [ ] Call `getPostsByHashtagUseCase.getPostsByHashtag(new Query(name, currentUserId, page, size))`.
- [ ] Map each `Post` to `PostSearchResponse.from(post)`.
- [ ] Return `200 OK`.

### URL path ordering concern

- [ ] Ensure `GET /api/v1/search/history` is declared **before** any path that could be confused with a search parameter. Since Spring MVC matches literal segments before path variables, this is not a conflict here, but confirm it anyway.

## Notes

- The `type` dispatch logic (switch or if-else) is the only logic allowed in the controller. Everything else delegates to use cases.
- `UserSearchResponse`, `HashtagSearchResponse`, `PostSearchResponse`, and `SearchHistoryResponse` DTOs are defined in TASK-8.7.
- The `currentUserId()` helper method already exists in other controllers (e.g., `PostController`, `NotificationController`). Copy the exact same pattern — do not re-implement it.
- For the `type` switch, use a `switch` expression (Java 21) for conciseness:
  ```java
  return switch (type) {
      case "users"    -> /* ... */;
      case "hashtags" -> /* ... */;
      case "posts"    -> /* ... */;
      default         -> ResponseEntity.badRequest()...;
  };
  ```
