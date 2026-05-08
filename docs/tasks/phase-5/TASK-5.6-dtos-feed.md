# TASK-5.6 — DTOs: FeedPageResponse & TrendingHashtagResponse

## Overview

Create the response DTO records for the feed and trending hashtag endpoints. These are Java records with static factory methods, consistent with `PostResponse`, `FollowResponse`, etc.

## Requirements

- Lives in `adapter/in/web/dto/`.
- Java **records** — no Lombok, no setters.
- Static `from(...)` factory method on each record.
- `FeedPageResponse` wraps the domain `FeedPage` and converts `nextCursor` UUID to `String`.

## File Locations

```
backend/src/main/java/com/instagram/adapter/in/web/dto/
├── FeedPageResponse.java
└── TrendingHashtagResponse.java
```

---

## Checklist

### `FeedPageResponse.java`

- [ ] Create the record:

  ```java
  public record FeedPageResponse(
          List<PostResponse> posts,
          String nextCursor
  ) {
      public static FeedPageResponse from(GetHomeFeedUseCase.FeedPage page) {
          List<PostResponse> postResponses = page.posts().stream()
                  .map(PostResponse::from)
                  .toList();
          String cursor = page.nextCursor() != null
                  ? page.nextCursor().toString()
                  : null;
          return new FeedPageResponse(postResponses, cursor);
      }

      /** Overload for explore feed — same structure, same mapping. */
      public static FeedPageResponse from(GetExploreFeedUseCase.FeedPage page) {
          List<PostResponse> postResponses = page.posts().stream()
                  .map(PostResponse::from)
                  .toList();
          String cursor = page.nextCursor() != null
                  ? page.nextCursor().toString()
                  : null;
          return new FeedPageResponse(postResponses, cursor);
      }
  }
  ```

- [ ] Import `PostResponse` from the same `dto` package.
- [ ] Import both `GetHomeFeedUseCase` and `GetExploreFeedUseCase` from `domain/port/in/`.

---

### `TrendingHashtagResponse.java`

- [ ] Create the record:

  ```java
  public record TrendingHashtagResponse(
          String id,
          String name,
          int postCount
  ) {
      public static TrendingHashtagResponse from(Hashtag hashtag) {
          return new TrendingHashtagResponse(
                  hashtag.getId().toString(),
                  hashtag.getName(),
                  hashtag.getPostCount()
          );
      }
  }
  ```

- [ ] Import `com.instagram.domain.model.Hashtag`.

## Notes

- `PostResponse` already maps all post fields including `likeCount`, `commentCount`, `likedByCurrentUser`, etc. — feed responses reuse it directly. Do **not** create a `FeedPostResponse` variant unless the fields differ.
- `nextCursor` is serialised as a plain string (UUID) in the JSON — the frontend parses it opaquely and sends it back as a query param.
