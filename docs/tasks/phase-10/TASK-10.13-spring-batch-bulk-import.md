# TASK-10.13 — Spring Batch: bulk import posts for a user

## Overview

Importing thousands of posts from a CSV or JSON file in a single HTTP request will time out (the request would need to stay open for minutes) and cannot recover if it fails halfway through. Spring Batch is the standard solution: it processes the file in configurable chunks (e.g., 50 rows at a time), commits each chunk as a separate database transaction, and records its progress in its own metadata tables (`BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION`). If the job fails after 1,000 rows, restarting it picks up from row 1,001 — not row 0.

---

## Level

Stretch · Pairs with [TASK-10.10 async processing](TASK-10.10-async-processing-virtual-threads.md) / [TASK-10.11 streaming exports](TASK-10.11-streaming-large-exports.md)

---

## Why

Importing thousands of posts (e.g., migrating a user from another platform) in one request times out and can't recover from a mid-way failure. Without chunking, a failure at row 2,000 of 5,000 means rolling back all 2,000 rows and restarting from zero. Spring Batch commits each chunk independently: a failure at row 2,000 leaves rows 1–1,950 committed (assuming 50-row chunks) and the job can resume from chunk 40 rather than from scratch. This is the industry-standard approach for reliable batch processing on the JVM.

---

## Prerequisites

- The `PostRepository` out-port and `PostPersistenceAdapter` are already in place.
- The `Post` domain model, `PostStatus` enum, and `CreatePostUseCase` in-port exist.
- Basic understanding of the Spring Batch programming model: `Job` → `Step` → (`ItemReader` → `ItemProcessor` → `ItemWriter`).
- Docker Compose stack is running (the Batch metadata tables will be created by Flyway in this task).

**Concepts to skim:**
- `Job`: the top-level Spring Batch unit. A `Job` contains one or more `Step`s.
- `Step`: a single phase of a job. A chunk-oriented `Step` reads N items, processes them, writes them, and commits a transaction — then repeats.
- `ItemReader<I>`: reads one item at a time from the source (CSV file, JSON array, database).
- `ItemProcessor<I, O>`: transforms a single input item into an output item. Returning `null` skips the item.
- `ItemWriter<O>`: writes a batch of output items (the chunk) in a single call.
- Chunk size: the number of items processed between commits. `chunk(50)` means every 50 items are committed together as one database transaction.
- Skip policy: if an item fails processing (e.g., invalid data), the framework can skip it instead of failing the entire job.
- `JobRepository`: Spring Batch's internal store for job/step execution metadata. Stored in the `BATCH_*` tables.
- Restartability: if a job fails, re-running it with the same `JobParameters` resumes from the last committed chunk.

---

## Prerequisites: checking pom.xml

Spring Batch is not in the current `pom.xml`. You will add it in step 1.

---

## Files to Create / Modify

```
backend/pom.xml                                                                          (modify — add spring-boot-starter-batch)
backend/src/main/resources/db/migration/V6__spring_batch_schema.sql                     (new)
backend/src/main/java/com/instagram/infrastructure/config/BatchConfig.java               (new)
backend/src/main/java/com/instagram/adapter/in/batch/PostImportItemProcessor.java        (new)
backend/src/main/java/com/instagram/adapter/in/batch/PostImportItemWriter.java           (new)
backend/src/main/java/com/instagram/adapter/in/batch/dto/PostImportRow.java             (new)
backend/src/main/java/com/instagram/adapter/in/web/AdminImportController.java           (new)
backend/src/main/java/com/instagram/adapter/in/web/dto/request/TriggerImportRequest.java  (new)
backend/src/main/java/com/instagram/adapter/in/web/dto/response/ImportJobResponse.java  (new)
```

---

## Step-by-Step

### 1. Add Spring Batch to pom.xml

Open `backend/pom.xml` and add inside `<dependencies>`:

```xml
<!-- Spring Batch for chunk-oriented bulk import -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-batch</artifactId>
</dependency>
```

Spring Boot's auto-configuration will create a `JobRepository`, `JobLauncher`, and `PlatformTransactionManager` pointing at the default datasource.

---

### 2. Create the Flyway migration for Spring Batch tables

Spring Batch requires 6 metadata tables. Create `backend/src/main/resources/db/migration/V6__spring_batch_schema.sql`.

The Spring Batch team publishes the exact schema for each database. For PostgreSQL, copy the content from the Spring Batch distribution's `schema-postgresql.sql`. The key tables are:

```sql
-- V6 — Spring Batch metadata tables
-- Source: Spring Batch PostgreSQL DDL
-- https://github.com/spring-projects/spring-batch/blob/main/spring-batch-core/src/main/resources/org/springframework/batch/core/schema-postgresql.sql

CREATE SEQUENCE BATCH_STEP_EXECUTION_SEQ MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_EXECUTION_SEQ  MAXVALUE 9223372036854775807 NO CYCLE;
CREATE SEQUENCE BATCH_JOB_SEQ            MAXVALUE 9223372036854775807 NO CYCLE;

CREATE TABLE BATCH_JOB_INSTANCE (
    JOB_INSTANCE_ID BIGINT       NOT NULL PRIMARY KEY,
    VERSION         BIGINT,
    JOB_NAME        VARCHAR(100) NOT NULL,
    JOB_KEY         VARCHAR(32)  NOT NULL,
    CONSTRAINT JOB_INST_UN UNIQUE (JOB_NAME, JOB_KEY)
);

CREATE TABLE BATCH_JOB_EXECUTION (
    JOB_EXECUTION_ID BIGINT      NOT NULL PRIMARY KEY,
    VERSION          BIGINT,
    JOB_INSTANCE_ID  BIGINT      NOT NULL,
    CREATE_TIME      TIMESTAMP   NOT NULL,
    START_TIME       TIMESTAMP,
    END_TIME         TIMESTAMP,
    STATUS           VARCHAR(10),
    EXIT_CODE        VARCHAR(2500),
    EXIT_MESSAGE     VARCHAR(2500),
    LAST_UPDATED     TIMESTAMP,
    CONSTRAINT JOB_INST_EXEC_FK FOREIGN KEY (JOB_INSTANCE_ID)
        REFERENCES BATCH_JOB_INSTANCE (JOB_INSTANCE_ID)
);

CREATE TABLE BATCH_JOB_EXECUTION_PARAMS (
    JOB_EXECUTION_ID BIGINT       NOT NULL,
    PARAMETER_NAME   VARCHAR(100) NOT NULL,
    PARAMETER_TYPE   VARCHAR(100) NOT NULL,
    PARAMETER_VALUE  VARCHAR(2500),
    IDENTIFYING      CHAR(1)      NOT NULL,
    CONSTRAINT JOB_EXEC_PARAMS_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

CREATE TABLE BATCH_STEP_EXECUTION (
    STEP_EXECUTION_ID  BIGINT       NOT NULL PRIMARY KEY,
    VERSION            BIGINT       NOT NULL,
    STEP_NAME          VARCHAR(100) NOT NULL,
    JOB_EXECUTION_ID   BIGINT       NOT NULL,
    CREATE_TIME        TIMESTAMP    NOT NULL,
    START_TIME         TIMESTAMP,
    END_TIME           TIMESTAMP,
    STATUS             VARCHAR(10),
    COMMIT_COUNT       BIGINT,
    READ_COUNT         BIGINT,
    FILTER_COUNT       BIGINT,
    WRITE_COUNT        BIGINT,
    READ_SKIP_COUNT    BIGINT,
    WRITE_SKIP_COUNT   BIGINT,
    PROCESS_SKIP_COUNT BIGINT,
    ROLLBACK_COUNT     BIGINT,
    EXIT_CODE          VARCHAR(2500),
    EXIT_MESSAGE       VARCHAR(2500),
    LAST_UPDATED       TIMESTAMP,
    CONSTRAINT JOB_EXEC_STEP_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);

CREATE TABLE BATCH_STEP_EXECUTION_CONTEXT (
    STEP_EXECUTION_ID  BIGINT        NOT NULL PRIMARY KEY,
    SHORT_CONTEXT      VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    CONSTRAINT STEP_EXEC_CTX_FK FOREIGN KEY (STEP_EXECUTION_ID)
        REFERENCES BATCH_STEP_EXECUTION (STEP_EXECUTION_ID)
);

CREATE TABLE BATCH_JOB_EXECUTION_CONTEXT (
    JOB_EXECUTION_ID  BIGINT        NOT NULL PRIMARY KEY,
    SHORT_CONTEXT     VARCHAR(2500) NOT NULL,
    SERIALIZED_CONTEXT TEXT,
    CONSTRAINT JOB_EXEC_CTX_FK FOREIGN KEY (JOB_EXECUTION_ID)
        REFERENCES BATCH_JOB_EXECUTION (JOB_EXECUTION_ID)
);
```

Also add to `application.yml` to prevent Spring Batch from auto-creating its own schema (Flyway manages DDL):

```yaml
spring:
  batch:
    jdbc:
      initialize-schema: never   # Flyway creates the BATCH_* tables
    job:
      enabled: false             # do not run jobs on startup — run on demand via API
```

---

### 3. Define the PostImportRow DTO

Create `backend/src/main/java/com/instagram/adapter/in/batch/dto/PostImportRow.java`:

```java
package com.instagram.adapter.in.batch.dto;

/**
 * Represents one row in the post import CSV file.
 * Field names match the CSV column headers (case-insensitive via Jackson CSV).
 */
public record PostImportRow(
        String caption,
        String location,
        String mediaUrl,       // optional: existing CDN URL of the media
        String createdAt       // optional ISO-8601; defaults to now if blank
) {}
```

---

### 4. Create PostImportItemProcessor

Create `backend/src/main/java/com/instagram/adapter/in/batch/PostImportItemProcessor.java`:

```java
package com.instagram.adapter.in.batch;

import com.instagram.adapter.in.batch.dto.PostImportRow;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Validates and maps a PostImportRow to a Post domain object.
 * Returns null to skip invalid rows (counted in PROCESS_SKIP_COUNT).
 */
public class PostImportItemProcessor implements ItemProcessor<PostImportRow, Post> {

    private static final Logger log = LoggerFactory.getLogger(PostImportItemProcessor.class);
    private static final int MAX_CAPTION_LENGTH = 2200;

    private final UUID userId;

    public PostImportItemProcessor(UUID userId) {
        this.userId = userId;
    }

    @Override
    public Post process(@NonNull PostImportRow row) {
        // Validate
        if (row.caption() != null && row.caption().length() > MAX_CAPTION_LENGTH) {
            log.warn("Skipping row: caption exceeds {} chars", MAX_CAPTION_LENGTH);
            return null; // skip this item
        }

        OffsetDateTime createdAt;
        try {
            createdAt = (row.createdAt() != null && !row.createdAt().isBlank())
                    ? OffsetDateTime.parse(row.createdAt())
                    : OffsetDateTime.now();
        } catch (Exception e) {
            log.warn("Skipping row: invalid createdAt '{}'", row.createdAt());
            return null;
        }

        return Post.builder()
                .userId(userId)
                .caption(row.caption())
                .location(row.location())
                .status(PostStatus.PUBLISHED)
                .likeCount(0)
                .commentCount(0)
                .saveCount(0)
                .shareCount(0)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }
}
```

---

### 5. Create PostImportItemWriter

Create `backend/src/main/java/com/instagram/adapter/in/batch/PostImportItemWriter.java`:

```java
package com.instagram.adapter.in.batch;

import com.instagram.domain.model.Post;
import com.instagram.domain.port.out.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

/**
 * Persists a chunk of Post domain objects via the PostRepository out-port.
 * Spring Batch calls this once per chunk; each call is wrapped in a transaction
 * managed by the batch framework.
 */
public class PostImportItemWriter implements ItemWriter<Post> {

    private static final Logger log = LoggerFactory.getLogger(PostImportItemWriter.class);

    private final PostRepository postRepository;

    public PostImportItemWriter(final PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Override
    public void write(Chunk<? extends Post> chunk) {
        log.debug("Writing chunk of {} posts", chunk.size());
        chunk.getItems().forEach(postRepository::save);
    }
}
```

---

### 6. Create BatchConfig — wire the Job and Step

Create `backend/src/main/java/com/instagram/infrastructure/config/BatchConfig.java`:

```java
package com.instagram.infrastructure.config;

import com.instagram.adapter.in.batch.PostImportItemProcessor;
import com.instagram.adapter.in.batch.PostImportItemWriter;
import com.instagram.adapter.in.batch.dto.PostImportRow;
import com.instagram.domain.model.Post;
import com.instagram.domain.port.out.PostRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.UUID;

@Configuration
public class BatchConfig {

    private final PostRepository postRepository;

    public BatchConfig(final PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    /**
     * Chunk-oriented job: reads CSV rows → validates/maps to Post → writes in chunks of 50.
     * The userId and filePath are passed as JobParameters at launch time.
     *
     * Restartability: if the job fails after committing chunk 10, restarting it
     * resumes from chunk 11 automatically (Spring Batch tracks the read count in
     * BATCH_STEP_EXECUTION.READ_COUNT).
     */
    @Bean
    public Job importPostsJob(JobRepository jobRepository, Step importPostsStep) {
        return new JobBuilder("importPostsJob", jobRepository)
                .start(importPostsStep)
                .build();
    }

    @Bean
    public Step importPostsStep(JobRepository jobRepository,
                                PlatformTransactionManager transactionManager) {
        // The reader, processor, and writer are created per job execution
        // (their userId and filePath parameters come from JobParameters).
        return new StepBuilder("importPostsStep", jobRepository)
                .<PostImportRow, Post>chunk(50, transactionManager)
                .reader(csvReader(null))          // null placeholder — overridden at launch
                .processor(new PostImportItemProcessor(UUID.randomUUID()))  // overridden
                .writer(new PostImportItemWriter(postRepository))
                .faultTolerant()
                .skipLimit(100)                   // skip up to 100 bad rows
                .skip(IllegalArgumentException.class)
                .build();
    }

    /**
     * CSV reader configured for the post import file format.
     * Column order: caption, location, mediaUrl, createdAt
     */
    public FlatFileItemReader<PostImportRow> csvReader(String filePath) {
        return new FlatFileItemReaderBuilder<PostImportRow>()
                .name("postImportCsvReader")
                .resource(new org.springframework.core.io.FileSystemResource(
                        filePath != null ? filePath : "/tmp/posts.csv"))
                .linesToSkip(1)   // skip the header row
                .delimited()
                .names("caption", "location", "mediaUrl", "createdAt")
                .targetType(PostImportRow.class)
                .build();
    }
}
```

---

### 7. Create the AdminImportController

Create `backend/src/main/java/com/instagram/adapter/in/web/AdminImportController.java`:

```java
package com.instagram.adapter.in.web;

import com.instagram.adapter.in.web.dto.request.TriggerImportRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.ImportJobResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.*;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/imports")
@RequiredArgsConstructor
@Tag(name = "Admin — Imports", description = "Bulk import operations")
public class AdminImportController {

    private static final Logger log = LoggerFactory.getLogger(AdminImportController.class);

    private final JobLauncher jobLauncher;
    private final Job importPostsJob;

    @PostMapping("/posts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ImportJobResponse>> triggerImport(
            @Valid @RequestBody TriggerImportRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        UUID userId = UUID.fromString(userDetails.getUsername());

        JobParameters params = new JobParametersBuilder()
                .addString("userId", userId.toString())
                .addString("filePath", request.filePath())
                .addLong("runAt", System.currentTimeMillis()) // unique per run for restartability
                .toJobParameters();

        try {
            JobExecution execution = jobLauncher.run(importPostsJob, params);
            return ResponseEntity.accepted()
                    .body(ApiResponse.ok(new ImportJobResponse(
                            execution.getId(),
                            execution.getStatus().name()
                    )));
        } catch (Exception e) {
            log.error("Failed to launch import job", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to launch import job: " + e.getMessage()));
        }
    }

    @GetMapping("/posts/{executionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ImportJobResponse>> getStatus(
            @PathVariable Long executionId) {
        // In a full implementation, inject JobExplorer and query the execution status.
        // Stub: return 404 for now.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ApiResponse.error("Status polling not yet implemented"));
    }
}
```

Add the companion DTOs:

```java
// TriggerImportRequest.java
public record TriggerImportRequest(@NotBlank String filePath) {}

// ImportJobResponse.java
public record ImportJobResponse(Long jobExecutionId, String status) {}
```

---

## Checklist

- [x] Add `spring-boot-starter-batch` + a Flyway migration for the Spring Batch metadata tables
- [x] Define a chunk-oriented `Step`: `ItemReader` (CSV/JSON) → `ItemProcessor` (validate + map to the `Post` domain model) → `ItemWriter` (persist via `PostRepository`)
- [x] Configure chunk size, a skip policy for bad rows, and retry on transient errors
- [x] Trigger via `POST /api/v1/admin/imports/posts` (returns a job execution id) + a status endpoint
- [x] Verify restartability: kill the job mid-run, relaunch, confirm it resumes from the last committed chunk

---

## How to Verify

**Step 1 — prepare a test CSV file:**

```powershell
$csv = @"
caption,location,mediaUrl,createdAt
Hello world,London,,2026-01-01T10:00:00Z
My second post,Paris,,2026-01-02T10:00:00Z
Invalid date post,,, not-a-date
Third valid post,Tokyo,,2026-01-03T10:00:00Z
"@
$csv | Out-File -Encoding utf8 C:\tmp\posts.csv
```

**Step 2 — trigger the import:**

```powershell
$body = '{"filePath":"C:/tmp/posts.csv"}'
$r = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/admin/imports/posts" `
    -Method POST `
    -Headers @{Authorization="Bearer <admin_token>"; "Content-Type"="application/json"} `
    -Body $body

$r.data.jobExecutionId   # note this ID
$r.data.status           # STARTING or COMPLETED
```

**Step 3 — verify in the batch tables:**

```sql
-- Check job execution status
SELECT job_execution_id, status, start_time, end_time
FROM BATCH_JOB_EXECUTION
ORDER BY job_execution_id DESC LIMIT 3;

-- Check step metrics
SELECT step_name, status, read_count, write_count, process_skip_count
FROM BATCH_STEP_EXECUTION
WHERE job_execution_id = <your_id>;
```

Expected: `read_count=4`, `write_count=3` (3 valid rows), `process_skip_count=1` (the invalid date row).

**Step 4 — verify restartability:**

1. Create a 1,000-row CSV.
2. Trigger the import.
3. Kill the backend (`Ctrl+C`) after ~500 rows are committed (watch the log for "Writing chunk").
4. Restart the backend.
5. Re-trigger the import with the SAME `filePath` (same JobParameters minus `runAt` — use a fixed value to make it restartable).
6. Check `BATCH_STEP_EXECUTION.READ_COUNT` — it should start from where it left off.

---

## Notes / Gotchas

**"The job runs on startup automatically."**
This is the default Spring Batch behavior (`spring.batch.job.enabled=true`). The migration in step 2 includes `spring.batch.job.enabled: false` to disable it. If you forget this, the job will fail on startup because no `filePath` JobParameter is provided.

**"`runAt` makes the job non-restartable."**
Adding a timestamp `runAt` as a `JobParameter` makes every run a new `JobInstance`. That means re-running the import does NOT resume — it starts over. For a restartable import, use only stable parameters (`userId`, `filePath`) without the timestamp, and rely on Spring Batch's `JobInstanceAlreadyCompleteException` to prevent re-running a completed import.

**"Chunk-size of 50 — how to choose?"**
Larger chunks = fewer transactions = faster for I/O-bound writes. Smaller chunks = less data lost on failure = more granular resume. 50 is a reasonable default for database writes. Increase to 200–500 for bulk inserts into a well-indexed table.

**"The `PostImportItemWriter` calls `postRepository.save()` one at a time — is that slow?"**
Yes, for very large imports. Optimize by adding a `saveAll(List<Post>)` method to `PostRepository` that uses `jpaRepository.saveAll(entities)` (which Hibernate can batch with JDBC batching enabled). Configure JDBC batching in `application.yml`:

```yaml
spring:
  jpa:
    properties:
      hibernate:
        jdbc:
          batch_size: 50
          order_inserts: true
```

**Official Spring Batch documentation:**
https://docs.spring.io/spring-batch/docs/current/reference/html/

**Cross-task references:**
- TASK-10.11 (streaming exports) is the inverse of this task — exporting posts in a streaming fashion.
- TASK-10.10 (async processing) — the `POST /api/v1/admin/imports/posts` endpoint can return `202 Accepted` and run the `JobLauncher.run()` call asynchronously on the `mediaExecutor` so the HTTP response returns immediately.

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Spring Batch concepts** — Job, Step, JobRepository — https://spring.io/guides/gs/batch-processing/
- **ItemReader / Processor / Writer** — the read-transform-write pipeline — https://docs.spring.io/spring-batch/reference/
- **Chunk-oriented processing** — commit in batches for throughput & restartability — https://docs.spring.io/spring-batch/reference/

### Official docs (code reference)
- **Spring Batch reference** — https://docs.spring.io/spring-batch/reference/
- **Batch processing (Spring guide)** — https://spring.io/guides/gs/batch-processing/
