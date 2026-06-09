# TASK-10.11 — Streaming large exports (CSV/ZIP, no OOM)

## Overview

A user export endpoint that reads all of a user's posts into a `List<Post>` before writing the file will run out of heap memory for accounts with thousands of posts. Streaming solves this by writing each row to the HTTP response output stream as it comes from the database, keeping only a small buffer in memory at any time. This task adds a `GET /api/v1/users/me/export` endpoint that streams a ZIP archive of the user's posts as CSV rows, using Spring's `StreamingResponseBody` and a cursor-based repository query to keep memory consumption flat regardless of post count.

---

## Level

Stretch · Pairs with [TASK-10.10 async processing](TASK-10.10-async-processing-virtual-threads.md) / [TASK-10.13 Spring Batch](TASK-10.13-spring-batch-bulk-import.md)

---

## Why

Building a full data export in memory blows up the heap on large accounts. If a user has 50,000 posts and each `Post` domain object is ~2 KB, loading them all into a `List<Post>` uses 100 MB just for that one request. With 10 concurrent export requests, that is 1 GB of heap — which causes `OutOfMemoryError` and brings down the entire application for everyone. Streaming writes bytes to the response as they are produced, so heap usage stays roughly constant (a few KB of buffer) throughout the entire download — whether the user has 100 posts or 100,000.

---

## Prerequisites

- TASK-10.8 (keyset pagination) — the streaming query uses the same cursor-based approach to read rows in batches rather than loading all rows at once.
- Understand `@Transactional(readOnly = true)` — the Hibernate session must stay open for the duration of the stream. A `readOnly` transaction is lighter and signals to the database that no write will occur.
- Know that `Stream<T>` from Spring Data JPA requires the session to remain open until the stream is consumed. Closing the stream is mandatory to release the cursor.

**Concepts to skim:**
- `StreamingResponseBody`: a Spring MVC interface. You return a lambda that is called with the `OutputStream` after Spring commits the response headers. Spring writes the response in a different thread, keeping the HTTP response alive until the lambda finishes.
- `Content-Disposition: attachment; filename="posts-export.zip"`: the HTTP header that tells the browser to download the response as a file rather than displaying it in the tab.
- `ZipOutputStream`: a Java standard library class that writes a ZIP archive to any `OutputStream`, entry by entry.
- Hibernate `JDBC fetch size`: when you call `repository.streamAll()`, Hibernate fetches rows in batches from Postgres to avoid loading the entire result set at once. The default fetch size is often 0 (load all rows at once) — setting it to 50–100 enables true streaming.
- `@Transactional(readOnly = true)`: marks a transaction as read-only. Postgres and Hibernate both apply optimizations for read-only transactions. The transaction must stay open until the stream is fully consumed.

---

## Files to Create / Modify

```
backend/src/main/java/com/instagram/domain/port/out/PostRepository.java             (modify — add streamByUserId)
backend/src/main/java/com/instagram/adapter/out/persistence/PostPersistenceAdapter.java  (modify — implement streamByUserId)
backend/src/main/java/com/instagram/adapter/out/persistence/repository/PostJpaRepository.java  (modify — add streaming query)
backend/src/main/java/com/instagram/domain/port/in/ExportUserDataUseCase.java        (new)
backend/src/main/java/com/instagram/application/service/UserDataExportService.java   (new)
backend/src/main/java/com/instagram/adapter/in/web/UserController.java              (modify — add export endpoint)
```

---

## Step-by-Step

### 1. Add a streaming query to PostJpaRepository

Open `backend/src/main/java/com/instagram/adapter/out/persistence/repository/PostJpaRepository.java`.

Add a `@Query` method that returns a `Stream<PostJpaEntity>`. Spring Data JPA streams the result from Postgres using a server-side cursor:

```java
import java.util.stream.Stream;

/**
 * Streams all non-deleted posts for a user in ascending creation order.
 * The caller MUST consume and close the stream inside a transaction.
 * Hibernate will fetch rows in batches (controlled by the JDBC fetch size
 * set in JpaConfig) rather than loading all rows into memory at once.
 */
@Query("""
        SELECT p FROM PostJpaEntity p
        WHERE p.user.id = :userId
          AND p.deletedAt IS NULL
        ORDER BY p.createdAt ASC
        """)
@QueryHints(value = {
        @QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "50")
})
Stream<PostJpaEntity> streamByUserId(@Param("userId") UUID userId);
```

Add the required import at the top:

```java
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.QueryHints;
```

---

### 2. Add streamByUserId to PostRepository out-port and adapter

Open `backend/src/main/java/com/instagram/domain/port/out/PostRepository.java`. Add:

```java
import java.util.stream.Stream;

// In the PostRepository interface:
Stream<Post> streamByUserId(UUID userId);
```

Open `backend/src/main/java/com/instagram/adapter/out/persistence/PostPersistenceAdapter.java`. Add:

```java
@Override
@Transactional(readOnly = true)
public Stream<Post> streamByUserId(UUID userId) {
    return jpaRepository.streamByUserId(userId)
            .map(this::toDomain);
}
```

The `@Transactional(readOnly = true)` here is critical — without it, the Hibernate session closes before the stream is consumed and you get a `LazyInitializationException` or the stream is exhausted immediately.

---

### 3. Create ExportUserDataUseCase in-port

Create `backend/src/main/java/com/instagram/domain/port/in/ExportUserDataUseCase.java`:

```java
package com.instagram.domain.port.in;

import java.io.OutputStream;
import java.util.UUID;

/**
 * Streams all of a user's posts as a ZIP/CSV export to the provided output stream.
 * The output stream is flushed and written incrementally — the implementor must
 * never collect the full result set into a list.
 */
public interface ExportUserDataUseCase {
    void exportPostsToCsv(Command command);

    record Command(UUID userId, OutputStream outputStream) {}
}
```

Note: `OutputStream` is passed in the `Command` record so the service can write directly to the HTTP response output stream without returning a byte array.

---

### 4. Create UserDataExportService

Create `backend/src/main/java/com/instagram/application/service/UserDataExportService.java`:

```java
package com.instagram.application.service;

import com.instagram.domain.port.in.ExportUserDataUseCase;
import com.instagram.domain.port.out.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import com.instagram.domain.model.Post;

@Service
public class UserDataExportService implements ExportUserDataUseCase {

    private static final Logger log = LoggerFactory.getLogger(UserDataExportService.class);

    private final PostRepository postRepository;

    public UserDataExportService(final PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Override
    @Transactional(readOnly = true)   // session stays open for the duration of the stream
    public void exportPostsToCsv(Command command) {
        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(command.outputStream(), StandardCharsets.UTF_8),
                true /* autoFlush */);

        writer.println("id,caption,location,status,like_count,comment_count,created_at");

        try (Stream<Post> posts = postRepository.streamByUserId(command.userId())) {
            posts.forEach(post -> {
                String line = String.join(",",
                        csvEscape(post.getId().toString()),
                        csvEscape(post.getCaption()),
                        csvEscape(post.getLocation()),
                        csvEscape(post.getStatus() != null ? post.getStatus().name() : ""),
                        String.valueOf(post.getLikeCount()),
                        String.valueOf(post.getCommentCount()),
                        post.getCreatedAt() != null ? post.getCreatedAt().toString() : ""
                );
                writer.println(line);
                // autoFlush=true means each line is sent to the client immediately
            });
        }

        log.info("Export completed for userId={}", command.userId());
    }

    /** Escapes a CSV field: wraps in quotes if it contains comma, quote, or newline. */
    private String csvEscape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
```

**Key design choices:**
- `try (Stream<Post> posts = ...)` — the `try-with-resources` ensures the Hibernate cursor is always closed, even if an exception occurs mid-export.
- `autoFlush=true` on the `PrintWriter` — each `println` call immediately flushes the buffer to the client. The user sees data arriving progressively rather than waiting for the full export.
- `@Transactional(readOnly = true)` — the database cursor (server-side) is held open for the duration of this method. If the transaction closes early, the `Stream` becomes invalid.

---

### 5. Add the export endpoint to UserController

Open `backend/src/main/java/com/instagram/adapter/in/web/UserController.java`.

Add the dependency injection and the endpoint:

```java
import com.instagram.domain.port.in.ExportUserDataUseCase;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

// Constructor — add the new use case:
private final ExportUserDataUseCase exportUserDataUseCase;

// Endpoint:
@GetMapping("/me/export")
@PreAuthorize("isAuthenticated()")
@Operation(summary = "Export all posts as a CSV file download")
public ResponseEntity<StreamingResponseBody> exportMyData(
        @AuthenticationPrincipal UserDetails userDetails) {

    UUID userId = UUID.fromString(userDetails.getUsername());

    StreamingResponseBody body = outputStream ->
        exportUserDataUseCase.exportPostsToCsv(
            new ExportUserDataUseCase.Command(userId, outputStream));

    return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"posts-export.csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(body);
}
```

`StreamingResponseBody` is a Spring MVC callback. Spring commits the `200 OK` and `Content-Disposition` headers immediately, then calls the lambda with the response `OutputStream`. The lambda runs until complete, writing rows as they come from the database. The HTTP connection stays open throughout.

---

### 6. Verify heap stays flat during a large export

Start the backend with a small heap to make the effect visible:

```powershell
# In backend/ directory
$env:MAVEN_OPTS = "-Xmx256m -Xms64m"
mvn spring-boot:run
```

Watch heap usage while downloading:

```powershell
# Terminal 1 — watch JVM memory (if Actuator is enabled from TASK-10.27)
while ($true) {
    $used = (Invoke-RestMethod http://localhost:8080/actuator/metrics/jvm.memory.used).measurements[0].value
    $max = (Invoke-RestMethod http://localhost:8080/actuator/metrics/jvm.memory.max).measurements[0].value
    Write-Host "Heap: $([math]::Round($used/1MB,1)) MB / $([math]::Round($max/1MB,1)) MB"
    Start-Sleep 2
}

# Terminal 2 — trigger the export
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/users/me/export" `
    -Headers @{Authorization="Bearer <token>"} `
    -OutFile C:\tmp\posts-export.csv
```

**Passing result:** Heap usage stays roughly flat (within ±20 MB) throughout the download. It does not grow linearly with the number of posts.

---

## Checklist

- [x] Add an endpoint returning `StreamingResponseBody` with `Content-Disposition: attachment`
- [x] Stream rows from a cursor-based / `Stream<>` repository query — never collect the full result into a list
- [x] Use `@Transactional(readOnly = true)` + a JDBC fetch size so Hibernate streams instead of buffering
- [x] Build the ZIP/CSV incrementally, flushing per chunk
- [x] Verify against a large dataset that heap stays flat throughout the download

---

## How to Verify

**Download produces a valid CSV:**

```powershell
Invoke-WebRequest -Uri "http://localhost:8080/api/v1/users/me/export" `
    -Headers @{Authorization="Bearer <token>"} `
    -OutFile C:\tmp\posts-export.csv

Get-Content C:\tmp\posts-export.csv | Select-Object -First 5
```

Expected: first line is the CSV header; subsequent lines are post rows.

**Content-Disposition header triggers file download:**

```powershell
$r = Invoke-WebRequest -Uri "http://localhost:8080/api/v1/users/me/export" `
    -Headers @{Authorization="Bearer <token>"}
$r.Headers["Content-Disposition"]
```

Expected: `attachment; filename="posts-export.csv"`

**Unauthenticated users are rejected:**

```powershell
Invoke-RestMethod -Uri "http://localhost:8080/api/v1/users/me/export"
```

Expected: `401 Unauthorized`

---

## Notes / Gotchas

**"`LazyInitializationException` when streaming posts."**
This means the Hibernate session closed before the `Stream<Post>` was fully consumed. Ensure `@Transactional(readOnly = true)` is on the service method (`exportPostsToCsv`) and that the `StreamingResponseBody` lambda is called within the same transaction boundary. With Spring MVC's `StreamingResponseBody`, the transaction opened by the service method may close before the lambda runs — verify by checking the transaction boundary.

If the exception persists, one safe workaround is to use the cursor-based keyset approach (TASK-10.8) explicitly: read 500 rows at a time in a loop, each batch in its own short transaction.

**"The entire export appears at once — it doesn't stream progressively."**
Check that `autoFlush` is enabled on the `PrintWriter` (shown in step 4). Also check that your HTTP client is not buffering — browsers buffer streaming responses. Use `curl --no-buffer` or `Invoke-WebRequest` with `-OutFile` to see streaming in action at the transport layer.

**"JDBC fetch size of 50 — is that right?"**
The fetch size controls how many rows Hibernate fetches from Postgres per round trip. With `fetchSize=50`, Postgres sends 50 rows at a time. For an export of 10,000 posts, this means 200 round trips — each brings 50 rows into the JVM, they are written to the output stream, then discarded. Increase the fetch size (100–500) if the export is slow due to many small round trips; decrease it if memory is tight.

**"How to add a ZIP wrapper?"**
Replace `PrintWriter` with `ZipOutputStream`, open a `ZipEntry` named `posts.csv`, and write the CSV bytes to the zip stream. Call `zipOut.closeEntry()` after each entry and `zipOut.finish()` when complete. Change the `Content-Disposition` filename to `posts-export.zip` and the `Content-Type` to `application/zip`.

**Cross-task references:**
- TASK-10.8 (keyset pagination) — the cursor-based batch fallback when `Stream<>` scoping is problematic.
- TASK-10.13 (Spring Batch) covers importing data in chunks — the inverse of this task.
- TASK-10.27 (Actuator + Micrometer) provides the `jvm.memory.used` metric used in the heap verification step.

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Streaming vs buffering** — write the response as you go instead of building it in memory (avoids OOM) — https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Transfer-Encoding
- **StreamingResponseBody** — Spring MVC's streaming write API — https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/StreamingResponseBody.html
- **Streaming query results** — read rows with a cursor, not all-at-once — https://docs.spring.io/spring-data/jpa/reference/

### Official docs (code reference)
- **OpenCSV (CSV writing)** — https://opencsv.sourceforge.net/
- **java.util.zip.ZipOutputStream** — https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/zip/ZipOutputStream.html
