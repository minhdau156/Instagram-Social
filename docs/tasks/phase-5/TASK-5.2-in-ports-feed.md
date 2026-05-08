# TASK-5.2 — In-Ports: GetHomeFeedUseCase & GetExploreFeedUseCase

## Overview

Define the two driving ports (in-ports) for the feed feature. Each interface has exactly one method and one inner `Query` record, following the project convention established in Phase 2 and 3.

## Requirements

- Lives in `domain/port/in/` — pure Java, no framework annotations.
- One file per use case.
- Return type is a `FeedPage` domain value object (defined here as a record).

## File Locations

```
backend/src/main/java/com/instagram/domain/port/in/
├── GetHomeFeedUseCase.java
└── GetExploreFeedUseCase.java
```

---

## Checklist

### `GetHomeFeedUseCase.java`

- [x] Create the interface:

  ```java
  public interface GetHomeFeedUseCase {

      FeedPage getHomeFeed(Query query);

      record Query(UUID userId, UUID cursor, int limit) {
          public Query {
              Objects.requireNonNull(userId, "userId must not be null");
              limit = Math.min(Math.max(limit, 1), 50); // clamp 1-50
          }
      }

      record FeedPage(List<Post> posts, UUID nextCursor) {}
  }
  ```

- [x] Import: `java.util.List`, `java.util.Objects`, `java.util.UUID`
- [x] Import: `com.instagram.domain.model.Post`

---

### `GetExploreFeedUseCase.java`

- [x] Create the interface following the **exact same pattern** as `GetHomeFeedUseCase`:

  ```java
  public interface GetExploreFeedUseCase {

      FeedPage getExploreFeed(Query query);

      record Query(UUID userId, UUID cursor, int limit) {
          public Query {
              Objects.requireNonNull(userId, "userId must not be null");
              limit = Math.min(Math.max(limit, 1), 50);
          }
      }

      record FeedPage(List<Post> posts, UUID nextCursor) {}
  }
  ```

## Notes

- `nextCursor` is the `id` of the **last** post in the returned list, or `null` when there are no more posts (i.e., the returned list has fewer than `limit` items).
- The `FeedPage` record is intentionally duplicated per use case — do not create a shared class to avoid coupling in-ports to each other.
