# TASK-5.5 — REST Controller: FeedController

## Overview

Expose the feed and explore endpoints via a REST controller. The controller delegates entirely to the use-case interfaces — no business logic lives here.

## Requirements

- Annotated with `@RestController` + `@RequestMapping("/api/v1")`.
- Constructor injection only.
- Reads the authenticated user's ID from `SecurityContextHolder`.
- Returns `ResponseEntity<ApiResponse<T>>` following the project's API envelope.
- `cursor` is an optional query param (UUID string); parse defensively.

## File Location

```
backend/src/main/java/com/instagram/adapter/in/web/FeedController.java
```

---

## Checklist

- [ ] Create `FeedController.java`:

  ```java
  @RestController
  @RequestMapping("/api/v1")
  @Tag(name = "Feed", description = "Home feed and explore endpoints")
  public class FeedController {

      private final GetHomeFeedUseCase getHomeFeedUseCase;
      private final GetExploreFeedUseCase getExploreFeedUseCase;
      private final FeedRepository feedRepository; // only for trending hashtags

      public FeedController(GetHomeFeedUseCase getHomeFeedUseCase,
                            GetExploreFeedUseCase getExploreFeedUseCase,
                            FeedRepository feedRepository) {
          this.getHomeFeedUseCase = getHomeFeedUseCase;
          this.getExploreFeedUseCase = getExploreFeedUseCase;
          this.feedRepository = feedRepository;
      }
  ```

  > **Note:** Trending hashtags is a thin read operation — it is acceptable to call `feedRepository.getTrendingHashtags()` directly from the controller without a dedicated use-case for MVP. If the project later needs ranking logic, extract a use-case then.

- [ ] Implement `GET /api/v1/feed`:

  ```java
  @GetMapping("/feed")
  @Operation(summary = "Get home feed (paginated)")
  public ResponseEntity<ApiResponse<FeedPageResponse>> getHomeFeed(
          @RequestParam(required = false) String cursor,
          @RequestParam(defaultValue = "20") int limit) {

      UUID userId = currentUserId();
      UUID cursorId = cursor != null ? UUID.fromString(cursor) : null;

      GetHomeFeedUseCase.FeedPage page = getHomeFeedUseCase.getHomeFeed(
              new GetHomeFeedUseCase.Query(userId, cursorId, limit));

      return ResponseEntity.ok(ApiResponse.success(FeedPageResponse.from(page)));
  }
  ```

- [ ] Implement `GET /api/v1/explore`:

  ```java
  @GetMapping("/explore")
  @Operation(summary = "Get explore feed (paginated)")
  public ResponseEntity<ApiResponse<FeedPageResponse>> getExploreFeed(
          @RequestParam(required = false) String cursor,
          @RequestParam(defaultValue = "20") int limit) {

      UUID userId = currentUserId();
      UUID cursorId = cursor != null ? UUID.fromString(cursor) : null;

      GetExploreFeedUseCase.FeedPage page = getExploreFeedUseCase.getExploreFeed(
              new GetExploreFeedUseCase.Query(userId, cursorId, limit));

      return ResponseEntity.ok(ApiResponse.success(FeedPageResponse.from(page)));
  }
  ```

- [ ] Implement `GET /api/v1/explore/hashtags`:

  ```java
  @GetMapping("/explore/hashtags")
  @Operation(summary = "Get trending hashtags")
  public ResponseEntity<ApiResponse<List<TrendingHashtagResponse>>> getTrendingHashtags(
          @RequestParam(defaultValue = "10") int limit) {

      List<TrendingHashtagResponse> hashtags = feedRepository
              .getTrendingHashtags(Math.min(limit, 30))
              .stream()
              .map(TrendingHashtagResponse::from)
              .toList();

      return ResponseEntity.ok(ApiResponse.success(hashtags));
  }
  ```

- [ ] Add private helper to resolve the current user's UUID from Spring Security:

  ```java
  private UUID currentUserId() {
      String userId = (String) SecurityContextHolder.getContext()
              .getAuthentication().getPrincipal();
      return UUID.fromString(userId);
  }
  ```

  > If the project uses a custom `UserPrincipal` object instead of a raw String, adapt accordingly — check `AuthController` or `PostController` for the existing pattern.

- [ ] Add `@SecurityRequirement(name = "bearerAuth")` to the class (OpenAPI annotation).

## Notes

- `IllegalArgumentException` from `UUID.fromString(cursor)` on a malformed cursor will bubble up to `GlobalExceptionHandler` — add a mapping there that returns HTTP 400 if not already present.
- Do not add pagination metadata beyond `nextCursor` — Spring Page objects are not used here (keyset, not offset).
