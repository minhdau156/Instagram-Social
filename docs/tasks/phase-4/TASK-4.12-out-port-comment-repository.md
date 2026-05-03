# TASK-4.12 — Out-Port: CommentRepository

## Overview

Define the `CommentRepository` out-port interface — the boundary contract between the domain layer and the persistence layer for all comment-related operations. `CommentService` depends only on this interface, never on JPA directly.

## Requirements

- Lives in `domain/port/out/` — no Spring or JPA annotations.
- All method signatures use domain types (`UUID`, `Comment`, `Pageable`) — not JPA entities.
- This interface is implemented by `CommentPersistenceAdapter` in the adapter layer (TASK-4.16).

## File Location

```
backend/src/main/java/com/instagram/domain/port/out/CommentRepository.java
```

---

## Checklist

- [x] Create `CommentRepository.java` interface with the following methods:

  ```java
  public interface CommentRepository {

      /**
       * Persists a new comment or saves an updated comment (edit/soft-delete).
       */
      Comment save(Comment comment);

      /**
       * Finds a comment by its surrogate ID. Returns empty if not found or DELETED.
       */
      Optional<Comment> findById(UUID commentId);

      /**
       * Returns paginated top-level comments for a post (parentId IS NULL and status != DELETED),
       * ordered by createdAt ascending.
       */
      Page<Comment> findByPostId(UUID postId, Pageable pageable);

      /**
       * Returns paginated replies for a given parent comment (parentId = parentId and status != DELETED),
       * ordered by createdAt ascending.
       */
      Page<Comment> findByParentId(UUID parentId, Pageable pageable);

      /**
       * Increments the reply_count counter for the parent comment.
       * Called when a new reply is added.
       */
      void incrementReplyCount(UUID parentCommentId);

      /**
       * Decrements the reply_count counter for the parent comment.
       * Called when a reply is deleted.
       */
      void decrementReplyCount(UUID parentCommentId);

      /**
       * Increments the like_count for a comment.
       */
      void incrementLikeCount(UUID commentId);

      /**
       * Decrements the like_count for a comment, minimum 0.
       */
      void decrementLikeCount(UUID commentId);

      /**
       * Increments the comment_count on the parent post.
       */
      void incrementPostCommentCount(UUID postId);

      /**
       * Decrements the comment_count on the parent post.
       */
      void decrementPostCommentCount(UUID postId);
  }
  ```

- [x] Import `java.util.Optional`, `java.util.UUID`, `org.springframework.data.domain.Page`, `org.springframework.data.domain.Pageable`
- [x] Add Javadoc on each method explaining preconditions and what `Page<Comment>` excludes (DELETED status)
