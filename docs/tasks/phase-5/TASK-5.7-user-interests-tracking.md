# TASK-5.7 — user_interests Tracking

## Overview

Implement the JPA persistence layer for the `user_interests` table and wire async interest signals into `LikeService` and `CommentService`. This data feeds the explore feed ranking in Phase 10.

## Requirements

- JPA entity and repository live in `adapter/out/persistence/`.
- `LikeService` and `CommentService` call the new interest tracker asynchronously (`@Async`) — no impact on like/comment response latency.
- Interest signals are additive: each like or comment increments the score by a fixed amount (e.g. `1.0`).

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/
├── UserInterestJpaEntity.java
└── UserInterestJpaRepository.java

# Modified files:
backend/src/main/java/com/instagram/domain/service/LikeService.java
backend/src/main/java/com/instagram/domain/service/CommentService.java
backend/src/main/java/com/instagram/infrastructure/config/AsyncConfig.java  (new)
```

---

## Checklist

### `UserInterestJpaEntity.java`

- [ ] Create the entity with a composite PK via `@EmbeddedId`:

  ```java
  @Embeddable
  public class UserInterestId implements Serializable {
      @Column(name = "user_id")    private UUID userId;
      @Column(name = "hashtag_id") private UUID hashtagId;
      // no-arg constructor, all-args constructor, equals(), hashCode()
  }

  @Entity
  @Table(name = "user_interests")
  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  public class UserInterestJpaEntity {

      @EmbeddedId
      private UserInterestId id;

      @Column(nullable = false)
      private BigDecimal score = BigDecimal.ONE;

      @Column(name = "updated_at", nullable = false)
      private OffsetDateTime updatedAt = OffsetDateTime.now();
  }
  ```

---

### `UserInterestJpaRepository.java`

- [ ] Create repository interface:

  ```java
  public interface UserInterestJpaRepository
          extends JpaRepository<UserInterestJpaEntity, UserInterestId> {

      @Modifying
      @Transactional
      @Query(value = """
              INSERT INTO user_interests (user_id, hashtag_id, score, updated_at)
              VALUES (:userId, :hashtagId, :delta, NOW())
              ON CONFLICT (user_id, hashtag_id)
              DO UPDATE SET score = user_interests.score + :delta,
                            updated_at = NOW()
              """, nativeQuery = true)
      void upsertScore(@Param("userId") UUID userId,
                       @Param("hashtagId") UUID hashtagId,
                       @Param("delta") BigDecimal delta);
  }
  ```

---

### `AsyncConfig.java`

- [ ] Create Spring async configuration to enable `@Async`:

  ```java
  @Configuration
  @EnableAsync
  public class AsyncConfig {

      @Bean(name = "interestExecutor")
      public Executor interestExecutor() {
          ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
          executor.setCorePoolSize(2);
          executor.setMaxPoolSize(4);
          executor.setQueueCapacity(100);
          executor.setThreadNamePrefix("interest-");
          executor.initialize();
          return executor;
      }
  }
  ```

---

### Interest signal service

- [ ] Create `UserInterestService.java` in `domain/service/` (or `adapter/out/persistence/` — use the adapter layer since it's JPA-dependent):

  Actually, since this directly touches JPA, place it in `adapter/out/persistence/` as a Spring `@Service`:

  ```java
  @Service
  public class UserInterestService {

      private final UserInterestJpaRepository repository;
      private final PostHashtagJpaRepository postHashtagRepository; // to resolve hashtag ids from a post

      public UserInterestService(UserInterestJpaRepository repository,
                                  PostHashtagJpaRepository postHashtagRepository) {
          this.repository = repository;
          this.postHashtagRepository = postHashtagRepository;
      }

      @Async("interestExecutor")
      public void recordLike(UUID userId, UUID postId) {
          // find all hashtag ids for this post and upsert score += 2.0
          postHashtagRepository.findHashtagIdsByPostId(postId).forEach(hashtagId ->
                  repository.upsertScore(userId, hashtagId, new BigDecimal("2.0")));
      }

      @Async("interestExecutor")
      public void recordComment(UUID userId, UUID postId) {
          // comment signals are weighted slightly lower
          postHashtagRepository.findHashtagIdsByPostId(postId).forEach(hashtagId ->
                  repository.upsertScore(userId, hashtagId, new BigDecimal("1.0")));
      }
  }
  ```

- [ ] Add `findHashtagIdsByPostId(UUID postId)` query method to `PostHashtagJpaRepository` (or create a new repository for `post_hashtags` if it doesn't exist):

  ```java
  @Query("SELECT ph.hashtagId FROM PostHashtagJpaEntity ph WHERE ph.postId = :postId")
  List<UUID> findHashtagIdsByPostId(@Param("postId") UUID postId);
  ```

---

### Wire signals into `LikeService`

- [ ] Inject `UserInterestService` into `LikeService` (constructor injection):

  ```java
  // After a successful likePost:
  userInterestService.recordLike(command.userId(), command.postId());
  ```

  > Call after the `postRepository.incrementLikeCount()` line. The `@Async` method returns immediately; errors are silently swallowed by the executor's default handler — acceptable for interest signals.

### Wire signals into `CommentService`

- [ ] Inject `UserInterestService` into `CommentService`:

  ```java
  // After a successful addComment:
  userInterestService.recordComment(command.userId(), command.postId());
  ```

## Notes

- Domain layer (`LikeService`, `CommentService`) now depends on `UserInterestService` which is in the adapter layer — this breaks hexagonal purity. To keep it clean, define a `UserInterestPort` out-port in `domain/port/out/` and implement it with `UserInterestService`. For MVP, direct injection is acceptable but flag it in a comment.
- The upsert SQL uses PostgreSQL `ON CONFLICT` syntax — this is a native query and will not work with other databases.
