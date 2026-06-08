# TASK-10.10 — Async processing with tuned executors + virtual threads

## Overview

Long-running side tasks — generating a thumbnail, kicking off a video transcode job, or logging to an audit service — should not block the HTTP request thread. If thumbnailing takes 2 seconds and your pool has 10 threads, 10 simultaneous upload requests will exhaust the thread pool and all subsequent requests will queue. This task shows you how to define a named, bounded `ThreadPoolTaskExecutor` bean, annotate the heavy method with `@Async`, and enable Java 21 virtual threads so the cost of a blocked I/O thread is nearly zero. The visible result is an endpoint that returns `202 Accepted` immediately while the heavy work continues in the background.

---

## Level

Stretch · Pairs with [TASK-10.11 streaming exports](TASK-10.11-streaming-large-exports.md) / [TASK-10.13 Spring Batch](TASK-10.13-spring-batch-bulk-import.md)

---

## Why

Long side-tasks (thumbnailing, transcode kickoff, import triggers) shouldn't block the request thread. In a traditional thread-per-request model, if a media processing job takes 3 seconds, one request holds a thread for 3 seconds. With 50 concurrent uploads, that is 50 threads blocked on I/O — exhausting a normal thread pool. Java 21 virtual threads (Project Loom) change this: a virtual thread that is blocked on I/O is "parked" at the JVM level and the underlying OS thread is freed for other work. You can run thousands of virtual threads cheaply where before you would exhaust 100 platform threads.

---

## Prerequisites

- The project is on Java 21 (confirmed in `pom.xml`: `<java.version>21</java.version>`).
- Spring Boot 3.3.4 supports virtual threads via `spring.threads.virtual.enabled=true`.
- Existing `AsyncConfig.java` in `infrastructure/config/` already defines an `interestExecutor` bean — this task adds a second, heavier executor for media work and demonstrates `@Async` on a service method.
- Basic understanding of `@Async` and `CompletableFuture<>`.

**Concepts to skim:**
- Platform thread: a classic Java thread backed by an OS thread. Creating thousands is expensive.
- Virtual thread (Project Loom, Java 21): a JVM-managed thread that is not 1:1 with an OS thread. When blocked on I/O, it parks and frees the underlying OS thread. You can have millions simultaneously.
- `@Async`: a Spring AOP annotation that runs the annotated method in a background executor thread instead of the calling thread.
- `CompletableFuture<Void>`: the return type for async void methods that callers might want to observe (completion, exception).
- `202 Accepted`: the HTTP status code meaning "the request has been accepted for processing, but the processing is not complete". The client may poll a status URL for the result.
- `ThreadPoolTaskExecutor`: Spring's wrapper around `java.util.concurrent.ThreadPoolExecutor` with friendly configuration properties.

---

## Files to Create / Modify

```
backend/src/main/java/com/instagram/infrastructure/config/AsyncConfig.java               (modify)
backend/src/main/java/com/instagram/application/service/PostService.java                  (modify — add async thumbnail stub)
backend/src/main/java/com/instagram/adapter/in/web/MediaController.java                   (modify — add 202 endpoint)
backend/src/main/resources/application.yml                                                 (modify — virtual thread toggle)
```

---

## Step-by-Step

### 1. Add a named media-processing executor to AsyncConfig

Open `backend/src/main/java/com/instagram/infrastructure/config/AsyncConfig.java`.

The existing file defines `interestExecutor` (2–4 threads for interest tracking). Add a second executor for heavier media work:

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

    /**
     * Executor for heavy media side-tasks: thumbnail generation,
     * transcode kickoff, EXIF stripping.
     *
     * Bounded at 4 threads: media tasks are CPU/IO-heavy and unbounded
     * parallelism would saturate the machine or external transcoder.
     * Queue capacity 50: reject tasks beyond this with CallerRunsPolicy
     * so the request thread completes the work synchronously as a backpressure mechanism.
     */
    @Bean(name = "mediaExecutor")
    public Executor mediaExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("media-worker-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

**Why `CallerRunsPolicy`?** If all 4 media workers are busy and the queue is full, the calling thread processes the task itself — the request blocks instead of throwing `RejectedExecutionException`. This is a graceful degradation: under extreme load, the response is slow but not an error.

Add the missing import at the top of `AsyncConfig.java`:

```java
import java.util.concurrent.ThreadPoolExecutor;
```

---

### 2. Add a @Async thumbnail stub to PostService

Open `backend/src/main/java/com/instagram/application/service/PostService.java`.

Add a private async method for thumbnail generation. This is currently a stub (the real implementation requires a transcoder library), but it demonstrates the pattern and the `@Async` wiring:

```java
import org.springframework.scheduling.annotation.Async;
import java.util.concurrent.CompletableFuture;

// Inside PostService class:

/**
 * Asynchronously generates a thumbnail for a newly uploaded video.
 * Runs on the mediaExecutor thread pool so the upload endpoint returns
 * immediately without waiting for thumbnail generation.
 *
 * @param mediaUrl the MinIO URL of the uploaded video
 * @param postId   the post UUID for logging context
 */
@Async("mediaExecutor")
public CompletableFuture<Void> generateThumbnailAsync(String mediaUrl, UUID postId) {
    log.info("Thumbnail generation started for post={}", postId);
    try {
        // TODO: call transcoder (FFmpeg, AWS MediaConvert, etc.)
        // For now, simulate work with a sleep for demonstration purposes
        Thread.sleep(200); // remove when real implementation exists
        log.info("Thumbnail generation completed for post={}", postId);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        log.warn("Thumbnail generation interrupted for post={}", postId);
    } catch (Exception e) {
        log.error("Thumbnail generation failed for post={}", postId, e);
    }
    return CompletableFuture.completedFuture(null);
}
```

**Important:** `@Async` only works when the method is called through a Spring-managed proxy — i.e., from a different bean. If `PostService` calls `this.generateThumbnailAsync(...)` internally, the AOP proxy is bypassed and the method runs synchronously. Call it from the controller or from another service, or inject `PostService` via `ApplicationContext` and call it on the proxy.

---

### 3. Add a 202 Accepted endpoint to MediaController

Open `backend/src/main/java/com/instagram/adapter/in/web/MediaController.java`.

Add an endpoint that accepts a media processing request and returns `202` immediately, while the work runs on the background executor:

```java
import java.util.Map;
import org.springframework.http.HttpStatus;

// Inside MediaController class:

/**
 * Accepts a media processing request and returns 202 immediately.
 * The heavy work (thumbnail generation, EXIF stripping) runs asynchronously
 * on the mediaExecutor thread pool.
 *
 * The client may poll GET /api/v1/media/jobs/{jobId} for status.
 * (Status polling endpoint is a stub — implement if needed.)
 */
@PostMapping("/process")
@PreAuthorize("isAuthenticated()")
@Operation(summary = "Trigger async media processing for an uploaded object")
public ResponseEntity<ApiResponse<Map<String, String>>> processMedia(
        @RequestBody @Valid ProcessMediaRequest request,
        @AuthenticationPrincipal UserDetails userDetails) {

    UUID userId = UUID.fromString(userDetails.getUsername());
    String jobId = UUID.randomUUID().toString();

    // Fire and forget — runs on mediaExecutor
    postService.generateThumbnailAsync(request.mediaUrl(), UUID.fromString(request.postId()));

    log.info("Media processing job accepted: jobId={} userId={}", jobId, userId);

    return ResponseEntity
            .status(HttpStatus.ACCEPTED)
            .body(ApiResponse.ok(Map.of(
                    "jobId", jobId,
                    "status", "accepted",
                    "statusUrl", "/api/v1/media/jobs/" + jobId
            )));
}
```

Add the companion request record in the DTOs package:

```java
// adapter/in/web/dto/request/ProcessMediaRequest.java
public record ProcessMediaRequest(
        @NotBlank String mediaUrl,
        @NotBlank String postId
) {}
```

---

### 4. Enable virtual threads

Open `backend/src/main/resources/application.yml` and add:

```yaml
spring:
  threads:
    virtual:
      enabled: true   # Java 21 virtual threads — request threads become virtual threads
```

With this enabled, Spring Boot 3.x replaces Tomcat's request-thread pool with virtual threads. Each HTTP request runs on a virtual thread. When that thread calls a blocking operation (JDBC, Redis, MinIO), the JVM parks the virtual thread and the underlying OS thread serves another virtual thread.

**Effect on your executors:** `ThreadPoolTaskExecutor` beans (like `mediaExecutor`) continue to use platform threads. Virtual threads improve the request-handling layer; your named executors are for CPU-bound or controlled-concurrency work that should remain bounded.

---

### 5. Verify via thread dump

With the app running, trigger the async endpoint and immediately take a thread dump:

```powershell
# Find the backend Java process PID
Get-Process -Name java | Select-Object Id, CPU

# Take a thread dump (replace <PID>)
& "$env:JAVA_HOME\bin\jstack" <PID> > C:\tmp\threaddump.txt

# Open and search for virtual threads
Select-String -Path C:\tmp\threaddump.txt -Pattern "VirtualThread"
```

**Passing result:** Lines containing `VirtualThread` appear in the dump — these are the request threads. Lines containing `media-worker-` are the bounded platform threads from your `mediaExecutor`.

```
#45 "VirtualThread[#1]/runnable@ForkJoinPool-1-worker-1" ...
#46 "media-worker-1" prio=5 ...
```

---

## Checklist

- [x] Define a named, bounded `ThreadPoolTaskExecutor` bean instead of relying on the default
- [x] Annotate the heavy side-task method with `@Async("…")` returning `CompletableFuture<>`
- [x] Enable virtual threads (`spring.threads.virtual.enabled=true`) and compare behaviour under load
- [x] Make one endpoint return `202` with a status URL while the work runs async
- [x] Confirm via thread dump / metric that the work runs off the request thread

---

## How to Verify

**202 response:**

```powershell
$body = '{"mediaUrl":"http://localhost:9000/instagram-media/test.mp4","postId":"00000000-0000-0000-0000-000000000001"}'
$r = Invoke-RestMethod -Uri "http://localhost:8080/api/v1/media/process" `
    -Method POST `
    -Headers @{Authorization="Bearer <token>"; "Content-Type"="application/json"} `
    -Body $body
$r.data.status   # should be "accepted"
```

**Passing result:** HTTP status `202`, response body contains `"status": "accepted"` and a `statusUrl`.

**Work runs off the request thread:**

Look at the backend log after calling the endpoint:

```
INFO  [http-nio-virtual-0] MediaController  - Media processing job accepted: jobId=...
INFO  [media-worker-1]      PostService     - Thumbnail generation started for post=...
INFO  [media-worker-1]      PostService     - Thumbnail generation completed for post=...
```

The `http-nio-virtual-X` thread (or similar virtual thread name) handles the HTTP request and returns `202`. The `media-worker-1` thread handles the actual work asynchronously — they are different threads.

**Virtual threads enabled:**

```powershell
Invoke-RestMethod http://localhost:8080/actuator/metrics/jvm.threads.live
```

With virtual threads enabled and under load, the live thread count should stay low (not spike to hundreds) even when handling many concurrent requests.

---

## Notes / Gotchas

**"`@Async` method doesn't run asynchronously — it blocks the request."**
The most common cause: the `@Async` method is called as `this.method()` inside the same class. Spring AOP proxies intercept calls between beans, not internal calls within the same object. Fix: inject the bean into itself via `@Autowired PostService self` and call `self.generateThumbnailAsync(...)`, or (better) move the async method to a dedicated `MediaProcessingService` bean that `PostService` injects.

**"`@Async` throws `RejectedExecutionException` under load."**
This means the executor's queue is full. The `CallerRunsPolicy` in the `mediaExecutor` bean handles this by running the task on the calling thread. If this happens frequently, either increase `queueCapacity` or reduce `max-pool-size` (paradoxically — more threads can increase contention on I/O-bound tasks).

**"Virtual threads cause issues with thread-local storage (e.g., MDC logging)."**
SLF4J's MDC uses `InheritableThreadLocal`, which is copied to child threads but not to virtual threads in all versions of SLF4J. If your MDC-based logging (TASK-10.26) shows missing `requestId` on virtual thread log lines, upgrade to SLF4J 2.x which supports `MDCAdapter` for virtual threads, or explicitly copy the MDC context in the `@Async` method.

**"What happens if `generateThumbnailAsync` throws an unchecked exception?"**
Since the return type is `CompletableFuture<Void>`, the caller can check `future.isCompletedExceptionally()` — but here we fire-and-forget (we do not store the future). Unhandled exceptions in `@Async` methods that return `void` are sent to Spring's `AsyncUncaughtExceptionHandler`. Configure one:

```java
@Bean
public AsyncUncaughtExceptionHandler asyncExceptionHandler() {
    return (throwable, method, params) ->
        log.error("Async exception in {}: {}", method.getName(), throwable.getMessage(), throwable);
}
```

**Official Spring Async documentation:**
https://docs.spring.io/spring-framework/reference/integration/scheduling.html#scheduling-annotation-support-async

**Cross-task references:**
- TASK-10.11 (streaming exports) also runs in a background context and benefits from the same virtual-thread setup.
- TASK-10.13 (Spring Batch) uses its own job-execution thread model; virtual threads do not apply there directly but the `202 Accepted` pattern for triggering batch jobs follows the same approach as this task.

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Virtual threads (Project Loom)** — cheap threads for blocking I/O — https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html
- **JEP 444: Virtual Threads** — the design rationale — https://openjdk.org/jeps/444
- **Platform vs virtual threads** — when each one wins — https://www.baeldung.com/java-virtual-thread-vs-thread
- **Spring @Async + executors** — offloading work to a thread pool — https://spring.io/guides/gs/async-method/

### Official docs (code reference)
- **Spring scheduling & async reference** — https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- **ThreadPoolTaskExecutor (javadoc)** — https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html
