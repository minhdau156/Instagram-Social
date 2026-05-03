# TASK-4.16 — Persistence Adapter: CommentPersistenceAdapter

## Overview

Implement `CommentPersistenceAdapter` — the adapter that bridges the domain's `CommentRepository` interface with `CommentJpaRepository`. It handles all domain ↔ JPA entity translation for the comment feature.

## Requirements

- Lives in `adapter/out/persistence/`.
- Annotated with `@Component`.
- Implements `CommentRepository` from TASK-4.12.
- Uses `CommentJpaRepository` from TASK-4.15.
- All write operations are `@Transactional`.
- No business logic — pure data mapping and delegation.

## File Location

```
backend/src/main/java/com/instagram/adapter/out/persistence/CommentPersistenceAdapter.java
```

---

## Checklist

- [x] Create `CommentPersistenceAdapter.java` annotated with `@Component`
- [x] Implement `CommentRepository`
- [x] Inject via constructor: `CommentJpaRepository`, `PostJpaRepository`
- [x] Declare all fields `private final`

- [x] Implement `save(Comment comment)`:
  ```java
  @Transactional
  public Comment save(Comment comment) {
      CommentJpaEntity entity = CommentJpaEntity.fromDomain(comment);
      return commentJpaRepository.save(entity).toDomain();
  }
  ```

- [x] Implement `findById(UUID commentId)`:
  ```java
  public Optional<Comment> findById(UUID commentId) {
      return commentJpaRepository.findById(commentId)
          .map(CommentJpaEntity::toDomain);
  }
  ```

- [x] Implement `findByPostId(UUID postId, Pageable pageable)`:
  ```java
  public Page<Comment> findByPostId(UUID postId, Pageable pageable) {
      return commentJpaRepository.findTopLevelByPostId(postId, pageable)
          .map(CommentJpaEntity::toDomain);
  }
  ```

- [x] Implement `findByParentId(UUID parentId, Pageable pageable)`:
  ```java
  public Page<Comment> findByParentId(UUID parentId, Pageable pageable) {
      return commentJpaRepository.findRepliesByParentId(parentId, pageable)
          .map(CommentJpaEntity::toDomain);
  }
  ```

- [x] Implement `incrementReplyCount(UUID parentCommentId)`:
  - Annotate with `@Transactional`
  - Delegate to `commentJpaRepository.incrementReplyCount(parentCommentId)`

- [x] Implement `decrementReplyCount(UUID parentCommentId)`:
  - Same pattern

- [x] Implement `incrementLikeCount(UUID commentId)`:
  - Delegate to `commentJpaRepository.incrementLikeCount(commentId)`

- [x] Implement `decrementLikeCount(UUID commentId)`:
  - Same pattern

- [x] Implement `incrementPostCommentCount(UUID postId)`:
  - Delegate to `postJpaRepository.incrementCommentCount(postId)`
  - Ensure `PostJpaRepository` has this `@Modifying @Query` method (add it in TASK-4.6 or here)

- [x] Implement `decrementPostCommentCount(UUID postId)`:
  - Delegate to `postJpaRepository.decrementCommentCount(postId)`
