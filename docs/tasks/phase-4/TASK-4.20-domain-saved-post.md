# TASK-4.20 — Domain Model & Out-Port: SavedPost

## Overview

Create the `SavedPost` domain model representing a user saving a post for later viewing, and define the `SavedPostRepository` out-port interface. This follows the same pattern as `Follow` + `FollowRepository`.

## Requirements

- Domain model lives in `domain/model/` — no framework annotations.
- Out-port lives in `domain/port/out/` — no Spring or JPA annotations.
- `SavedPost` uses a hand-written Builder.
- Composite natural key: (`postId`, `userId`).

## File Locations

```
backend/src/main/java/com/instagram/domain/model/SavedPost.java
backend/src/main/java/com/instagram/domain/port/out/SavedPostRepository.java
```

---

## Checklist

### `SavedPost.java`

- [x] Create `SavedPost.java` with fields:
  ```java
  private UUID id;
  private UUID postId;
  private UUID userId;
  private Instant savedAt;
  ```

- [x] Implement private no-arg constructor (used by Builder only)

- [x] Implement static inner `Builder` with fluent setters and `build()` that validates `id`, `postId`, `userId`

- [x] Add static factory method `of(UUID postId, UUID userId)`:
  ```java
  public static SavedPost of(UUID postId, UUID userId) {
      return new Builder()
          .id(UUID.randomUUID())
          .postId(postId)
          .userId(userId)
          .savedAt(Instant.now())
          .build();
  }
  ```

- [x] Implement public getters (no setters)

---

### `SavedPostRepository.java`

- [x] Create `SavedPostRepository.java` interface:
  ```java
  public interface SavedPostRepository {

      /**
       * Persists a new saved post record.
       */
      SavedPost save(SavedPost savedPost);

      /**
       * Removes the saved post record for a given user and post.
       * No-op if record does not exist.
       */
      void delete(UUID postId, UUID userId);

      /**
       * Returns true if the given user has already saved the given post.
       */
      boolean existsByPostIdAndUserId(UUID postId, UUID userId);

      /**
       * Returns paginated saved posts for a user, ordered by savedAt descending.
       */
      Page<SavedPost> findByUserId(UUID userId, Pageable pageable);
  }
  ```

- [x] Add Javadoc on each method

---

### Unit Tests — `SavedPostTest.java`

- [x] **`of_createsSavedPost_withCorrectFields()`**
- [x] **`builder_throwsException_whenPostIdIsNull()`**
- [x] **`builder_throwsException_whenUserIdIsNull()`**
