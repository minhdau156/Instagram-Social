# TASK-4.13 — In-Ports: Comment Use Cases

## Overview

Define all use-case interfaces for the Comment feature. Each use case is a single-method interface with a nested `Command` or `Query` record. These are the "driving" ports — `CommentController` calls them and `CommentService` implements them.

## Requirements

- Lives in `domain/port/in/` — no Spring or JPA annotations.
- Each interface declares exactly **one** method.
- Commands/Queries use `record` types nested inside the interface.
- Follow the naming and structure of existing use cases (e.g., `FollowUserUseCase`, `GetFollowersUseCase`).

## File Locations

```
backend/src/main/java/com/instagram/domain/port/in/comment
├── AddCommentUseCase.java
├── EditCommentUseCase.java
├── DeleteCommentUseCase.java
├── GetCommentsUseCase.java
└── GetRepliesUseCase.java
```

---

## Checklist

### `AddCommentUseCase.java`

- [x] Create interface:
  ```java
  public interface AddCommentUseCase {
      Comment addComment(Command command);

      record Command(
          UUID postId,
          UUID userId,
          String content,
          UUID parentId   // nullable — null = top-level comment
      ) {}
  }
  ```

### `EditCommentUseCase.java`

- [x] Create interface:
  ```java
  public interface EditCommentUseCase {
      Comment editComment(Command command);

      record Command(UUID commentId, UUID userId, String newContent) {}
  }
  ```

### `DeleteCommentUseCase.java`

- [x] Create interface:
  ```java
  public interface DeleteCommentUseCase {
      void deleteComment(Command command);

      record Command(UUID commentId, UUID userId) {}
  }
  ```

### `GetCommentsUseCase.java`

- [x] Create interface:
  ```java
  public interface GetCommentsUseCase {
      Page<Comment> getComments(Query query);

      record Query(UUID postId, int page, int size) {}
  }
  ```
  - Returns top-level (non-reply) active comments only.

### `GetRepliesUseCase.java`

- [x] Create interface:
  ```java
  public interface GetRepliesUseCase {
      Page<Comment> getReplies(Query query);

      record Query(UUID commentId, int page, int size) {}
  }
  ```

- [x] Verify that all `Command` / `Query` field types are `UUID`, `int`, `String`, or domain types only.
- [x] Ensure nullable fields (`parentId` in `AddCommentUseCase.Command`) are documented with a comment.
