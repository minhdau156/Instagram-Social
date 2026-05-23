# TASK-10.48 — Scheduled jobs with distributed locking (ShedLock)

## Overview

Spring's `@Scheduled` annotation fires the annotated method on every JVM instance in the cluster — so if you have three backend replicas, a nightly cleanup job runs three times simultaneously, potentially corrupting data or tripling the workload. ShedLock solves this by storing a lock row in the database before executing each job: the first instance that acquires the lock runs the job, and the other instances skip their execution for that tick. This task adds ShedLock to the project, creates a Flyway migration for its lock table, and wraps three cleanup jobs with distributed locks.

## Level

**Stretch** · Pairs with [TASK-10.46](TASK-10.46-docker-compose.md) (full Compose stack for running two instances locally) and [TASK-10.12](TASK-10.12-chunked-resumable-upload.md) (stale upload session cleanup) and [TASK-10.24](TASK-10.24-idempotency-keys.md) (old idempotency key cleanup).

## Why

`@Scheduled` was designed for single-instance applications. In any horizontally scaled deployment — even two replicas — the job fires on every instance at the same time with no coordination. A "purge orphaned media" job running on three replicas at once will each DELETE the same rows, causing deadlocks and duplicate log entries. A "delete expired idempotency keys" job running twice may delete a key mid-flight while the other instance is still processing a request that depends on it. ShedLock uses a shared JDBC lock table (the same Postgres database the app already uses) to guarantee that for each named job, exactly one instance holds the lock during each scheduled tick — others see the lock row and exit immediately. The lock also has a `lockAtMostFor` timeout so a dead instance cannot hold the lock forever.

## Prerequisites

- Spring Boot backend running (Phase 0–9 complete).
- `@EnableScheduling` either already present or added during this task.
- Postgres running and accessible; Flyway applied.
- Familiarity with Spring's `@Scheduled` annotation.
- **Concepts to skim:**
  - _ShedLock_ — a library that stores a lock record in a shared store (JDBC, Redis, Mongo, etc.) before executing a scheduled task. See [ShedLock GitHub](https://github.com/lukas-krecan/ShedLock).
  - _`lockAtMostFor`_ — the maximum time a lock is held, even if the holding instance crashes. After this duration the lock is released automatically. Set it slightly longer than the job's maximum expected runtime.
  - _`lockAtLeastFor`_ — the minimum time the lock is held. Prevents the same instance from re-acquiring the lock immediately after a very fast job completes, which would defeat the "one execution per tick" guarantee on fast cron schedules.
  - _JDBC lock provider_ — ShedLock writes and reads a row in the `shedlock` table. The row contains the lock name, the lock-until timestamp, the locked-at timestamp, and the locking hostname.

## Files to Create / Modify

```
backend/pom.xml
  (modify — add shedlock-spring and shedlock-provider-jdbc-template)

backend/src/main/resources/db/migration/V<next>__add_shedlock_table.sql
  (new — Flyway migration creating the shedlock table)

backend/src/main/java/com/instagram/infrastructure/config/SchedulerConfig.java
  (new — enables scheduling and configures the JDBC lock provider)

backend/src/main/java/com/instagram/infrastructure/scheduler/MediaCleanupJob.java
  (new — purges orphaned media references)

backend/src/main/java/com/instagram/infrastructure/scheduler/StaleUploadSessionJob.java
  (new — expires stale multipart-upload sessions, TASK-10.12 dependent)

backend/src/main/java/com/instagram/infrastructure/scheduler/IdempotencyKeyCleanupJob.java
  (new — deletes expired idempotency keys, TASK-10.24 dependent)
```

## Step-by-Step

### 1. Add ShedLock dependencies to `pom.xml`

Open `backend/pom.xml` and add the two ShedLock artifacts inside the `<dependencies>` section:

```xml
<!-- ShedLock — distributed lock for @Scheduled jobs -->
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-spring</artifactId>
    <version>5.16.0</version>
</dependency>
<dependency>
    <groupId>net.javacrumbs.shedlock</groupId>
    <artifactId>shedlock-provider-jdbc-template</artifactId>
    <version>5.16.0</version>
</dependency>
```

Check [Maven Central](https://central.sonatype.com/artifact/net.javacrumbs.shedlock/shedlock-spring) for the latest `5.x` version and update accordingly.

### 2. Find the next Flyway migration version number

```powershell
ls backend/src/main/resources/db/migration/ | Sort-Object Name | Select-Object -Last 3
```

Bash:

```bash
ls backend/src/main/resources/db/migration/ | sort | tail -3
```

Identify the highest `V` prefix (e.g. `V3__add_fts_indexes.sql`) and use the next number (e.g. `V4`).

### 3. Create the Flyway migration for the `shedlock` table

Create `backend/src/main/resources/db/migration/V4__add_shedlock_table.sql` (adjust the version number as needed):

```sql
-- ShedLock requires a table to coordinate distributed job execution.
-- Rows are inserted/updated to represent acquired locks.
-- See: https://github.com/lukas-krecan/ShedLock#jdbctemplate

CREATE TABLE IF NOT EXISTS shedlock (
    name         VARCHAR(64)  NOT NULL,
    lock_until   TIMESTAMPTZ  NOT NULL,
    locked_at    TIMESTAMPTZ  NOT NULL,
    locked_by    VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
);
```

This is the exact schema required by `shedlock-provider-jdbc-template`. Do not rename the columns.

### 4. Create `SchedulerConfig.java`

Create `backend/src/main/java/com/instagram/infrastructure/config/SchedulerConfig.java`:

```java
package com.instagram.infrastructure.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

import javax.sql.DataSource;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M") // 10 minutes default
public class SchedulerConfig {

    /**
     * Configures ShedLock to use the application's Postgres database as the
     * lock store. The shedlock table must exist before the application starts
     * (created by the Flyway migration added in this task).
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime() // Use database clock to avoid clock-skew issues
                .build()
        );
    }
}
```

**Why `usingDbTime()`:** When multiple application instances have slightly different system clocks, using `System.currentTimeMillis()` for lock timestamps can lead to subtle race conditions. `usingDbTime()` delegates all timestamp comparisons to the database clock, which is the authoritative single source of truth.

### 5. Create `MediaCleanupJob.java`

Create `backend/src/main/java/com/instagram/infrastructure/scheduler/MediaCleanupJob.java`:

```java
package com.instagram.infrastructure.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Purges post_media rows that are no longer referenced by any post.
 * This handles the case where a user starts a post draft (uploading media)
 * but never completes the post submission.
 *
 * Runs once per hour. Distributed lock ensures only one instance runs per tick.
 */
@Component
public class MediaCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(MediaCleanupJob.class);

    private final JdbcTemplate jdbcTemplate;

    public MediaCleanupJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 * * * *") // Every hour at minute 0
    @SchedulerLock(
        name = "MediaCleanupJob",
        lockAtMostFor = "PT55M",   // Release lock after 55 min even if job hangs
        lockAtLeastFor = "PT5M"    // Hold lock at least 5 min to prevent re-entry
    )
    public void purgeOrphanedMedia() {
        log.info("[MediaCleanupJob] Starting orphaned media purge");

        // Delete post_media rows where the parent post has been hard-deleted.
        // For soft-deleted posts (deleted_at IS NOT NULL), media is kept.
        int deleted = jdbcTemplate.update("""
            DELETE FROM post_media pm
            WHERE NOT EXISTS (
                SELECT 1 FROM posts p WHERE p.id = pm.post_id
            )
            """);

        log.info("[MediaCleanupJob] Purged {} orphaned media rows", deleted);
    }
}
```

### 6. Create `StaleUploadSessionJob.java`

This job depends on a `multipart_upload_sessions` table created in [TASK-10.12](TASK-10.12-chunked-resumable-upload.md). If TASK-10.12 is not yet complete, create the job file but comment out the `jdbcTemplate.update` body until the table exists.

Create `backend/src/main/java/com/instagram/infrastructure/scheduler/StaleUploadSessionJob.java`:

```java
package com.instagram.infrastructure.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expires multipart upload sessions that were started but never completed.
 * A session is considered stale if it has not been updated in 24 hours.
 * Depends on: TASK-10.12 (multipart_upload_sessions table).
 *
 * Runs every 30 minutes.
 */
@Component
public class StaleUploadSessionJob {

    private static final Logger log = LoggerFactory.getLogger(StaleUploadSessionJob.class);

    private final JdbcTemplate jdbcTemplate;

    public StaleUploadSessionJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 */30 * * * *") // Every 30 minutes
    @SchedulerLock(
        name = "StaleUploadSessionJob",
        lockAtMostFor = "PT25M",
        lockAtLeastFor = "PT2M"
    )
    public void expireStaleUploadSessions() {
        log.info("[StaleUploadSessionJob] Starting stale upload session expiry");

        // Requires TASK-10.12. Remove the guard once the table exists.
        int expired = jdbcTemplate.update("""
            UPDATE multipart_upload_sessions
            SET status = 'EXPIRED'
            WHERE status = 'IN_PROGRESS'
              AND updated_at < NOW() - INTERVAL '24 hours'
            """);

        log.info("[StaleUploadSessionJob] Expired {} stale upload sessions", expired);
    }
}
```

### 7. Create `IdempotencyKeyCleanupJob.java`

This job depends on an `idempotency_keys` table created in [TASK-10.24](TASK-10.24-idempotency-keys.md).

Create `backend/src/main/java/com/instagram/infrastructure/scheduler/IdempotencyKeyCleanupJob.java`:

```java
package com.instagram.infrastructure.scheduler;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes idempotency keys that have passed their expiry time.
 * Keys are kept for 24 hours after creation to cover any reasonable retry window.
 * Depends on: TASK-10.24 (idempotency_keys table).
 *
 * Runs daily at 03:00 UTC.
 */
@Component
public class IdempotencyKeyCleanupJob {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyCleanupJob.class);

    private final JdbcTemplate jdbcTemplate;

    public IdempotencyKeyCleanupJob(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 3 * * *") // Daily at 03:00 UTC
    @SchedulerLock(
        name = "IdempotencyKeyCleanupJob",
        lockAtMostFor = "PT50M",
        lockAtLeastFor = "PT10M"
    )
    public void deleteExpiredIdempotencyKeys() {
        log.info("[IdempotencyKeyCleanupJob] Starting expired idempotency key cleanup");

        // Requires TASK-10.24. Remove the guard once the table exists.
        int deleted = jdbcTemplate.update("""
            DELETE FROM idempotency_keys
            WHERE expires_at < NOW()
            """);

        log.info("[IdempotencyKeyCleanupJob] Deleted {} expired idempotency keys", deleted);
    }
}
```

### 8. Verify the lock table is created on startup

Start the application (with Postgres running) and check for the Flyway migration log:

```powershell
cd backend && mvn spring-boot:run 2>&1 | Select-String "shedlock\|V4__"
```

Expected output:

```
Successfully applied 1 migration to schema "public", now at version V4 (execution time 00:00.021s)
```

Then check the `shedlock` table exists in Postgres:

```powershell
# If psql is available:
psql -h localhost -U instagram -d instagram -c "\d shedlock"
```

Expected output: a table description with columns `name`, `lock_until`, `locked_at`, `locked_by`.

### 9. Confirm one-instance-per-tick with two local instances

Run the backend on two different ports simultaneously (the second instance uses a different server port):

**Terminal 1** (default port 8080):

```powershell
cd backend
mvn spring-boot:run
```

**Terminal 2** (port 8081):

```powershell
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

Wait for a scheduled job to fire (or temporarily change the cron to `*/30 * * * * *` — every 30 seconds — for testing).

**Expected log behaviour:**

- One terminal prints: `[MediaCleanupJob] Starting orphaned media purge` and `[MediaCleanupJob] Purged 0 orphaned media rows`.
- The other terminal prints nothing for that tick (it tried to acquire the lock, saw it was held, and skipped).

After the job completes, check the lock row in Postgres:

```sql
SELECT name, lock_until, locked_by FROM shedlock;
```

Expected:

```
name              | lock_until                  | locked_by
------------------+-----------------------------+-----------------------------
MediaCleanupJob   | 2026-05-23 04:05:00+00      | instagram-backend-instance-1
```

## Checklist

- [ ] Add `shedlock-spring` + a JDBC lock provider and a Flyway migration for the `shedlock` table
- [ ] Create `@Scheduled` jobs: purge orphaned media, expire stale multipart-upload sessions (TASK-10.12), delete old idempotency keys (TASK-10.24)
- [ ] Wrap each job with `@SchedulerLock(name, lockAtMostFor)`
- [ ] Run two instances locally and confirm the job runs once per tick, not twice
- [ ] Emit a log line / metric per job run for auditability

## How to Verify

1. Start the application. Check that Flyway created the `shedlock` table:

   ```sql
   SELECT COUNT(*) FROM shedlock;
   ```

   Passing result: query succeeds (table exists); count is 0 before any job fires.

2. Trigger a job manually by temporarily shortening its cron expression to `*/15 * * * * *` (every 15 seconds), restarting, and watching the logs. After the job fires, check:

   ```sql
   SELECT * FROM shedlock WHERE name = 'MediaCleanupJob';
   ```

   Passing result: a row exists with `locked_by` matching the hostname of the running instance.

3. Start a second instance on port 8081 (Step 9 above). Wait for the next tick. Check logs on both terminals.

   Passing result: exactly one instance logs `[MediaCleanupJob] Starting orphaned media purge`. The other logs nothing for that tick.

4. Confirm the lock row is released after the job completes by checking that `lock_until` is in the past.

## Notes / Gotchas

- **`@EnableSchedulerLock` replaces `@EnableScheduling`.** You do not need both. `@EnableSchedulerLock` from ShedLock activates its proxy around scheduled methods, and it implicitly enables the Spring scheduler as well. Keep `@EnableScheduling` only if you have `@Scheduled` methods that intentionally run on every instance (no lock needed).

- **`lockAtMostFor` must be greater than the maximum possible job runtime.** If a job takes 20 minutes but `lockAtMostFor = "PT5M"`, ShedLock releases the lock after 5 minutes, allowing a second instance to start the same job while the first is still running. Set `lockAtMostFor` to at least 2× the typical runtime as a safety buffer.

- **Cron expressions in Spring use 6 fields (seconds, minutes, hours, day-of-month, month, day-of-week)**, not the 5-field Unix cron format. `"0 0 3 * * *"` means "at 03:00:00 every day". The leading `0` is the seconds field.

- **`StaleUploadSessionJob` and `IdempotencyKeyCleanupJob` depend on tables from TASK-10.12 and TASK-10.24 respectively.** If those tasks are not yet complete, the application will fail to start because the SQL in `jdbcTemplate.update` references non-existent tables. Guard the SQL behind an existence check or simply comment out the `jdbcTemplate.update` line and add `// TODO: uncomment when TASK-10.12 is complete` until the table is available.

- **ShedLock version compatibility.** ShedLock 5.x requires Spring Boot 3.x and Java 17+. This project uses Spring Boot 3.3.4 and Java 21, so it is compatible. If you see `NoSuchMethodError` on startup, check that the ShedLock version matches the Spring Boot version.

- **`usingDbTime()` is important in containerised deployments.** Each Docker container has its own system clock, which may drift from the others. `usingDbTime()` ensures all lock expiry calculations use the Postgres server clock, making the behaviour correct even if containers drift by a few seconds.

- Related tasks: [TASK-10.46](TASK-10.46-docker-compose.md), [TASK-10.12](TASK-10.12-chunked-resumable-upload.md), [TASK-10.24](TASK-10.24-idempotency-keys.md).

- Official reference: [ShedLock GitHub](https://github.com/lukas-krecan/ShedLock), [ShedLock JDBC provider](https://github.com/lukas-krecan/ShedLock#jdbctemplate).
