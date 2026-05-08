# TASK-5.1 — Out-Port: FeedRepository

## Overview

Define the `FeedRepository` driven port (out-port) that abstracts all feed-related data access. The domain service depends only on this interface — it never touches JPA directly.

## Requirements

- Lives in `domain/port/out/` — **no** Spring or JPA imports.
- Uses only domain types: `Post` (from `domain/model/Post.java`) and primitive Java types.
- Keyset pagination: cursor is the `UUID` of the last seen post (`null` for first page).

## File Location

```
backend/src/main/java/com/instagram/domain/port/out/FeedRepository.java
```

---

## Checklist

- [x] Create `FeedRepository.java`:

  ```java
  public interface FeedRepository {

      /**
       * Returns posts from users that userId follows, newest first.
       * cursor: UUID of last seen post (null = first page).
       */
      List<Post> getHomeFeed(UUID userId, UUID cursor, int limit);

      /**
       * Returns posts NOT from followed users, ranked by engagement.
       * Excludes posts the user has already interacted with (based on user_interests).
       */
      List<Post> getExploreFeed(UUID userId, UUID cursor, int limit);

      /**
       * Returns trending hashtags ordered by weekly_count DESC.
       */
      List<Hashtag> getTrendingHashtags(int limit);
  }
  ```

- [x] Import: `java.util.List`, `java.util.UUID`
- [x] Import domain types: `com.instagram.domain.model.Post`, `com.instagram.domain.model.Hashtag`

## Notes

- `Hashtag` already exists from Phase 2 (`domain/model/Hashtag.java`). Do **not** create a new class.
- The `cursor` approach is keyset pagination — it avoids the `OFFSET` performance problem on large tables.
- `limit` is the caller-supplied page size; the service caps it at 50.
