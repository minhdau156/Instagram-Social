# TASK-4.15 — JPA Entity & Repository: Comment

## Overview

Create the JPA persistence classes for the `comments` table. `CommentJpaEntity` includes a self-referential relationship for nested replies (parent-child). The repository provides custom JPQL queries to filter by status and parent.

## Requirements

- Lives in `adapter/out/persistence/`.
- `@Entity @Table(name = "comments")`.
- Self-referential `@ManyToOne` for `parentId`.
- `status` stored as a string enum via `@Enumerated(EnumType.STRING)`.
- Repository uses custom JPQL queries to exclude `DELETED` comments.

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/
├── CommentJpaEntity.java
└── CommentJpaRepository.java
```

---

## Checklist

### `CommentJpaEntity.java`

- [x] Create `CommentJpaEntity.java`:
  ```java
  @Entity
  @Table(name = "comments")
  public class CommentJpaEntity {

      @Id
      @Column(name = "id", updatable = false, nullable = false)
      private UUID id;

      @Column(name = "post_id", nullable = false)
      private UUID postId;

      @Column(name = "user_id", nullable = false)
      private UUID userId;

      @ManyToOne(fetch = FetchType.LAZY)
      @JoinColumn(name = "parent_id")
      private CommentJpaEntity parent;  // nullable

      @Column(name = "body", nullable = false, length = 2200)
      private String content;

      @Column(name = "like_count", nullable = false)
      private int likeCount;

      @Column(name = "reply_count", nullable = false)
      private int replyCount;

      @Enumerated(EnumType.STRING)
      @Column(name = "status", nullable = false)
      private CommentStatus status;

      @Column(name = "created_at", nullable = false, updatable = false)
      @CreationTimestamp
      private Instant createdAt;

      @Column(name = "updated_at", nullable = false)
      @UpdateTimestamp
      private Instant updatedAt;

      // no-arg constructor, all-args constructor, getters
  }
  ```
- [x] Implement `equals()` and `hashCode()` based on `id`
- [x] Add `toDomain()` method that maps `CommentJpaEntity` → `Comment` domain object
- [x] Add static `fromDomain(Comment)` factory method

### `CommentJpaRepository.java`

- [x] Create repository interface:
  ```java
  public interface CommentJpaRepository extends JpaRepository<CommentJpaEntity, UUID> {

      /**
       * Top-level comments for a post — parent is null, status is ACTIVE, ordered by createdAt ASC.
       */
      @Query("SELECT c FROM CommentJpaEntity c WHERE c.postId = :postId " +
             "AND c.parent IS NULL AND c.status <> 'DELETED' ORDER BY c.createdAt ASC")
      Page<CommentJpaEntity> findTopLevelByPostId(
          @Param("postId") UUID postId, Pageable pageable);

      /**
       * Replies for a parent comment — status is ACTIVE, ordered by createdAt ASC.
       */
      @Query("SELECT c FROM CommentJpaEntity c WHERE c.parent.id = :parentId " +
             "AND c.status <> 'DELETED' ORDER BY c.createdAt ASC")
      Page<CommentJpaEntity> findRepliesByParentId(
          @Param("parentId") UUID parentId, Pageable pageable);

      @Modifying
      @Query("UPDATE CommentJpaEntity c SET c.replyCount = c.replyCount + 1 WHERE c.id = :id")
      void incrementReplyCount(@Param("id") UUID id);

      @Modifying
      @Query("UPDATE CommentJpaEntity c SET c.replyCount = GREATEST(c.replyCount - 1, 0) WHERE c.id = :id")
      void decrementReplyCount(@Param("id") UUID id);

      @Modifying
      @Query("UPDATE CommentJpaEntity c SET c.likeCount = c.likeCount + 1 WHERE c.id = :id")
      void incrementLikeCount(@Param("id") UUID id);

      @Modifying
      @Query("UPDATE CommentJpaEntity c SET c.likeCount = GREATEST(c.likeCount - 1, 0) WHERE c.id = :id")
      void decrementLikeCount(@Param("id") UUID id);
  }
  ```

- [x] Verify `CommentStatus` enum is importable from the domain model in the persistence layer (or create a JPA-specific copy if your architecture requires it)
