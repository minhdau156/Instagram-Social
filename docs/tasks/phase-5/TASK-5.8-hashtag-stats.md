# TASK-5.8 — hashtag_stats Scheduled Rollup

## Overview

Implement the JPA layer for the `hashtag_stats` table and a nightly `@Scheduled` task that recalculates weekly hashtag engagement counts from `post_hashtags` and `post_likes`.

## Requirements

- JPA entity and repository in `adapter/out/persistence/`.
- Scheduled job in `infrastructure/config/` or a dedicated `infrastructure/scheduler/` package.
- `@EnableScheduling` must be added to the application config.
- Weekly count = number of posts with that hashtag created in the last 7 days.

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/
├── HashtagStatsJpaEntity.java
└── HashtagStatsJpaRepository.java

backend/src/main/java/com/instagram/infrastructure/scheduler/
└── HashtagStatsScheduler.java

# Modified:
backend/src/main/java/com/instagram/infrastructure/config/SchedulerConfig.java (new)
```

---

## Checklist

### `HashtagStatsJpaEntity.java`

- [x] Create entity (primary key is `hashtag_id` — single-column PK referencing `hashtags`):

  ```java
  @Entity
  @Table(name = "hashtag_stats")
  @Getter
  @NoArgsConstructor
  @AllArgsConstructor
  public class HashtagStatsJpaEntity {

      @Id
      @Column(name = "hashtag_id")
      private UUID hashtagId;

      @Column(name = "weekly_count", nullable = false)
      private int weeklyCount;

      @Column(name = "updated_at", nullable = false)
      private OffsetDateTime updatedAt;
  }
  ```

---

### `HashtagStatsJpaRepository.java`

- [x] Create repository interface:

  ```java
  public interface HashtagStatsJpaRepository
          extends JpaRepository<HashtagStatsJpaEntity, UUID> {

      @Modifying
      @Transactional
      @Query(value = """
              INSERT INTO hashtag_stats (hashtag_id, weekly_count, updated_at)
              SELECT ph.hashtag_id,
                     COUNT(*) AS weekly_count,
                     NOW()
              FROM post_hashtags ph
              JOIN posts p ON p.id = ph.post_id
              WHERE p.created_at >= NOW() - INTERVAL '7 days'
                AND p.deleted_at IS NULL
              GROUP BY ph.hashtag_id
              ON CONFLICT (hashtag_id)
              DO UPDATE SET weekly_count = EXCLUDED.weekly_count,
                            updated_at   = NOW()
              """, nativeQuery = true)
      void rollupWeeklyCounts();
  }
  ```

---

### `SchedulerConfig.java`

- [x] Enable scheduling:

  ```java
  @Configuration
  @EnableScheduling
  public class SchedulerConfig {}
  ```

---

### `HashtagStatsScheduler.java`

- [x] Create the scheduled job:

  ```java
  @Component
  public class HashtagStatsScheduler {

      private static final Logger log = LoggerFactory.getLogger(HashtagStatsScheduler.class);

      private final HashtagStatsJpaRepository hashtagStatsJpaRepository;

      public HashtagStatsScheduler(HashtagStatsJpaRepository hashtagStatsJpaRepository) {
          this.hashtagStatsJpaRepository = hashtagStatsJpaRepository;
      }

      // Runs every night at 02:00 server time
      @Scheduled(cron = "0 0 2 * * *")
      public void rollupHashtagStats() {
          log.info("Starting hashtag stats weekly rollup");
          hashtagStatsJpaRepository.rollupWeeklyCounts();
          log.info("Hashtag stats rollup complete");
      }
  }
  ```

## Notes

- The `rollupWeeklyCounts` query is an upsert — safe to run multiple times (idempotent).
- `@Scheduled(cron = "0 0 2 * * *")` fires at 02:00 every day. The cron format is: `second minute hour day-of-month month day-of-week`.
- For testing, the scheduler can be triggered manually via a test that calls `rollupHashtagStats()` directly — no need to wait for the cron.
- If `hashtag_stats` is empty on a fresh DB, the trending hashtags endpoint returns an empty list — this is correct and expected until the first rollup runs. Seed data can be inserted in a Flyway migration if needed for local dev.
