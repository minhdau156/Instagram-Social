# TASK-5.4 — JPA Adapter: FeedJpaQueryAdapter

## Overview

Implement the `FeedRepository` out-port using native SQL queries via Spring Data JPA. This adapter translates the domain's keyset-cursor requests into optimised SQL and maps JPA entities back to domain `Post` objects.

## Requirements

- Implements `FeedRepository` from `domain/port/out/`.
- Annotated with `@Component`.
- Uses `EntityManager` with native queries (or `@Query(nativeQuery = true)` on a helper repository).
- Contains private `toDomain(PostJpaEntity)` mapping method — never exposes JPA entities outside this class.

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/
├── FeedJpaQueryAdapter.java      ← main adapter (implements FeedRepository)
└── FeedJpaRepository.java        ← Spring Data interface with @Query methods
```

---

## Checklist

### `FeedJpaRepository.java`

- [x] Create interface extending `JpaRepository<PostJpaEntity, UUID>`:

  ```java
  public interface FeedJpaRepository extends JpaRepository<PostJpaEntity, UUID> {

      @Query(value = """
              SELECT p.* FROM posts p
              JOIN follows f ON f.following_id = p.user_id
              WHERE f.follower_id = :userId
                AND f.is_approved = true
                AND (:cursor IS NULL OR p.id < :cursor)
                AND p.deleted_at IS NULL
              ORDER BY p.created_at DESC
              LIMIT :limit
              """, nativeQuery = true)
      List<PostJpaEntity> findHomeFeed(
              @Param("userId") UUID userId,
              @Param("cursor") UUID cursor,
              @Param("limit") int limit);

      @Query(value = """
              SELECT p.* FROM posts p
              WHERE p.user_id NOT IN (
                  SELECT f.following_id FROM follows f
                  WHERE f.follower_id = :userId AND f.is_approved = true
              )
                AND p.user_id <> :userId
                AND p.deleted_at IS NULL
                AND (:cursor IS NULL OR p.id < :cursor)
              ORDER BY (p.like_count + p.comment_count) DESC, p.created_at DESC
              LIMIT :limit
              """, nativeQuery = true)
      List<PostJpaEntity> findExploreFeed(
              @Param("userId") UUID userId,
              @Param("cursor") UUID cursor,
              @Param("limit") int limit);

      @Query(value = """
              SELECT h.id, h.name, h.post_count, h.created_at,
                     hs.weekly_count, hs.updated_at
              FROM hashtags h
              JOIN hashtag_stats hs ON hs.hashtag_id = h.id
              ORDER BY hs.weekly_count DESC
              LIMIT :limit
              """, nativeQuery = true)
      List<Object[]> findTrendingHashtags(@Param("limit") int limit);
  }
  ```

- [x] Import: `org.springframework.data.jpa.repository.JpaRepository`, `@Query`, `@Param`

---

### `FeedJpaQueryAdapter.java`

- [x] Create class annotated with `@Component` implementing `FeedRepository`:

  ```java
  @Component
  public class FeedJpaQueryAdapter implements FeedRepository {

      private final FeedJpaRepository feedJpaRepository;
      private final PostJpaRepository postJpaRepository; // for toDomain mapping reuse

      public FeedJpaQueryAdapter(FeedJpaRepository feedJpaRepository,
                                  PostJpaRepository postJpaRepository) {
          this.feedJpaRepository = feedJpaRepository;
          this.postJpaRepository = postJpaRepository;
      }

      @Override
      public List<Post> getHomeFeed(UUID userId, UUID cursor, int limit) {
          return feedJpaRepository.findHomeFeed(userId, cursor, limit)
                  .stream()
                  .map(this::toDomain)
                  .toList();
      }

      @Override
      public List<Post> getExploreFeed(UUID userId, UUID cursor, int limit) {
          return feedJpaRepository.findExploreFeed(userId, cursor, limit)
                  .stream()
                  .map(this::toDomain)
                  .toList();
      }

      @Override
      public List<Hashtag> getTrendingHashtags(int limit) {
          return feedJpaRepository.findTrendingHashtags(limit)
                  .stream()
                  .map(this::toHashtagDomain)
                  .toList();
      }

      private Post toDomain(PostJpaEntity entity) {
          // Reuse the same mapping already in PostPersistenceAdapter
          // Copy the toDomain logic here, or extract a shared PostMapper utility
          // Do NOT call PostPersistenceAdapter directly — copy/adapt the mapping
          return Post.builder()
                  .id(entity.getId())
                  .userId(entity.getUserId())
                  .caption(entity.getCaption())
                  .location(entity.getLocation())
                  .status(entity.getStatus())
                  .likeCount(entity.getLikeCount())
                  .commentCount(entity.getCommentCount())
                  .saveCount(entity.getSaveCount())
                  .shareCount(entity.getShareCount())
                  .createdAt(entity.getCreatedAt())
                  .updatedAt(entity.getUpdatedAt())
                  .build();
      }

      private Hashtag toHashtagDomain(Object[] row) {
          // row[0]=id, row[1]=name, row[2]=postCount, row[3]=createdAt
          return Hashtag.builder()
                  .id((UUID) row[0])
                  .name((String) row[1])
                  .postCount(((Number) row[2]).intValue())
                  .build();
      }
  }
  ```

- [x] Verify that `PostJpaEntity` exposes all fields used in `toDomain()` — add getters if missing.
- [x] Confirm `Hashtag.java` (domain model) has a builder — if not, add one following the `Post.java` pattern.

## Notes

- The SQL uses `p.id < :cursor` for keyset pagination. Because UUIDs are v4 (random), this works as a stable keyset because results are ordered by `created_at DESC` and the cursor is the last-seen post id. For strict correctness, `created_at` and `id` should both be in the keyset — this is a known simplification acceptable for MVP.
- The explore query excludes the user's own posts (`p.user_id <> :userId`) to avoid showing their own content in discovery.
- `findTrendingHashtags` returns raw `Object[]` — cast carefully using the column order from the SELECT clause.
