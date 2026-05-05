# TASK-4.24 — Domain Model & Out-Port: PostShare

## Overview

Create the `PostShare` domain model representing a user sharing a post (via link copy or direct message), and define the `ShareRepository` out-port interface.

## Requirements

- Domain model lives in `domain/model/` — no framework annotations.
- Out-port lives in `domain/port/out/` — no Spring or JPA annotations.
- `PostShare` uses a hand-written Builder.
- `ShareType` enum defines the sharing mechanism.

## File Locations

```
backend/src/main/java/com/instagram/domain/model/PostShare.java
backend/src/main/java/com/instagram/domain/model/ShareType.java
backend/src/main/java/com/instagram/domain/port/out/ShareRepository.java
```

---

## Checklist

### `ShareType.java`

- [x] Create `ShareType.java` enum:
  ```java
  public enum ShareType {
      /** User copied a link to the post. */
      LINK,

      /** User sent the post to another user via DM. */
      DM
  }
  ```

### `PostShare.java`

- [x] Create `PostShare.java` with fields:
  ```java
  private UUID id;
  private UUID postId;
  private UUID sharerId;
  private UUID recipientId;   // nullable — only for ShareType.DM
  private ShareType shareType;
  private Instant createdAt;
  ```

- [x] Implement private no-arg constructor and static inner `Builder` with fluent setters
- [x] `build()` validates `id`, `postId`, `sharerId`, `shareType` are non-null
- [x] Add static factory method `of(UUID postId, UUID sharerId, UUID recipientId, ShareType shareType)`:
  ```java
  public static PostShare of(UUID postId, UUID sharerId, UUID recipientId, ShareType shareType) {
      return new Builder()
          .id(UUID.randomUUID())
          .postId(postId)
          .sharerId(sharerId)
          .recipientId(recipientId)
          .shareType(shareType)
          .createdAt(Instant.now())
          .build();
  }
  ```
- [x] Implement public getters (no setters)

---

### `ShareRepository.java`

- [x] Create `ShareRepository.java` interface:
  ```java
  public interface ShareRepository {

      /**
       * Persists a new share record.
       */
      PostShare save(PostShare share);

      /**
       * Returns all share records for a post (analytics purpose).
       */
      List<PostShare> findByPostId(UUID postId);
  }
  ```

---

### Unit Tests — `PostShareTest.java`

- [x] **`of_createsPostShare_withCorrectFields()`**
- [x] **`of_dmShare_hasRecipientId()`**
- [x] **`of_linkShare_recipientIdIsNull()`**
- [x] **`builder_throwsException_whenShareTypeIsNull()`**
