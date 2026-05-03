# TASK-4.10 — Domain Model: Comment

## Overview

Create the `Comment` domain entity representing a comment on a post, with support for nested replies (parent-child relationship). This is a pure Java class with **no framework dependencies**. It models the full comment lifecycle: creation, editing, soft-deletion, and threading.

## Requirements

- Must live in `domain/model/` — **no** `@Entity`, `@Component`, or Lombok annotations.
- Fields map directly to the `comments` table in `schema.sql`.
- Uses a hand-written **Builder** pattern (consistent with `Post.java`, `Follow.java`).
- Business behaviour lives inside the entity as copy-returning methods.
- Enum `CommentStatus` must be defined to represent the comment state.

## File Locations

```
backend/src/main/java/com/instagram/domain/model/Comment.java
backend/src/main/java/com/instagram/domain/model/CommentStatus.java
```

## Notes

- `parentId` is nullable — `null` means a top-level comment; non-null means a reply.
- `withEdit(String newContent)` returns a new `Comment` with the updated `content` and a new `updatedAt`.
- `withSoftDelete()` returns a new `Comment` with `status = DELETED` and `content` cleared/replaced by a placeholder.
- `likeCount` and `replyCount` are denormalized counters, not resolved collections.
- Do **not** add `@Entity`, `@Table`, or any persistence annotations here.

---

## Checklist

### `CommentStatus.java`

- [x] Create `CommentStatus.java` enum:
  ```java
  public enum CommentStatus {
      ACTIVE,
      DELETED
  }
  ```

### `Comment.java`

- [x] Create `Comment.java` with fields:
  ```java
  private UUID id;
  private UUID postId;
  private UUID userId;
  private UUID parentId;          // nullable — null = top-level comment
  private String content;
  private int likeCount;
  private int replyCount;
  private CommentStatus status;
  private Instant createdAt;
  private Instant updatedAt;
  ```

- [x] Implement private no-arg constructor (used by Builder only)

- [x] Implement static inner `Builder` class with:
  - [x] Fluent setter methods returning `this` for each field
  - [x] `build()` that validates `id`, `postId`, `userId`, `content`, `status` are not null

- [x] Implement a private `copy()` helper returning a mutable `Builder` copy

- [x] Implement `withEdit(String newContent)`:
  ```java
  public Comment withEdit(String newContent) {
      return this.copy()
          .content(newContent)
          .updatedAt(Instant.now())
          .build();
  }
  ```

- [x] Implement `withSoftDelete()`:
  ```java
  public Comment withSoftDelete() {
      return this.copy()
          .status(CommentStatus.DELETED)
          .content("[deleted]")
          .updatedAt(Instant.now())
          .build();
  }
  ```

- [x] Add static factory method `of(UUID postId, UUID userId, String content, UUID parentId)`:
  ```java
  public static Comment of(UUID postId, UUID userId, String content, UUID parentId) {
      return new Builder()
          .id(UUID.randomUUID())
          .postId(postId)
          .userId(userId)
          .parentId(parentId)
          .content(content)
          .likeCount(0)
          .replyCount(0)
          .status(CommentStatus.ACTIVE)
          .createdAt(Instant.now())
          .updatedAt(Instant.now())
          .build();
  }
  ```

- [x] Implement public getters for all fields (no setters)

---

### Unit Tests — `CommentTest.java`

- [x] **`of_createsComment_withCorrectFields()`**
- [x] **`of_topLevelComment_hasNullParentId()`**
- [x] **`withEdit_returnsNewInstance_withUpdatedContent()`**
- [x] **`withEdit_doesNotMutateOriginal()`**
- [x] **`withSoftDelete_setsStatusToDeleted_andClearsContent()`**
- [x] **`withSoftDelete_doesNotMutateOriginal()`**
- [x] **`builder_throwsException_whenContentIsNull()`**
