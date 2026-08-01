# Tuned Executors + Virtual Threads

## What is it?
- **Executors** (`java.util.concurrent.ExecutorService`) manage a pool of worker threads that run submitted tasks (`Runnable`/`Callable`) asynchronously. "Tuned" means configuring pool size, queue capacity, rejection policy, and keep-alive time to match the workload instead of relying on defaults.
- **Virtual threads** (Project Loom, stable since Java 21) are lightweight threads managed by the JVM rather than the OS. Millions can exist cheaply since they aren't mapped 1:1 to OS threads — they're scheduled onto a small pool of "carrier" OS threads and unmount from the carrier when blocked on I/O.

## Why use them?
- **Tuned executors**: give explicit control over concurrency limits to avoid exhausting memory/CPU/DB connections — important for CPU-bound work or fixed-capacity downstream resources (e.g., a DB pool of 20 connections).
- **Virtual threads**: solve the thread-per-request scalability problem. Platform threads are expensive (~1MB stack, OS context-switch cost), capping concurrency for I/O-bound work. Virtual threads allow simple blocking/synchronous code to scale to thousands/millions of concurrent I/O-bound tasks without reactive/async complexity (WebFlux, CompletableFuture chains).

## How to use it

### Tuned executor (classic)
```java
ExecutorService executor = new ThreadPoolExecutor(
    10,                     // core pool size
    50,                     // max pool size
    60L, TimeUnit.SECONDS,  // idle thread keep-alive
    new LinkedBlockingQueue<>(500), // bounded queue
    new ThreadPoolExecutor.CallerRunsPolicy() // backpressure policy
);
```
Tune pool size based on workload: CPU-bound → close to `Runtime.getRuntime().availableProcessors()`; I/O-bound → higher, since threads spend time waiting rather than computing.

### Virtual threads (Java 21+)
```java
ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
executor.submit(() -> {
    // blocking I/O call (DB query, HTTP call, etc.)
    // JVM unmounts this virtual thread from its carrier while blocked
});
```

### Spring Boot 3.2+ integration
```yaml
spring:
  threads:
    virtual:
      enabled: true
```
Makes the embedded server handle each incoming request on a virtual thread automatically — no controller code changes needed.

## When to use it in real life
- **Tuned platform-thread executors**: CPU-bound batch processing, image/video transcoding, scheduled jobs with bounded parallelism, or when integrating libraries not yet virtual-thread-safe (some use `synchronized` blocks that pin virtual threads to carriers, negating the benefit).
- **Virtual threads**: high-throughput REST APIs/microservices that spend most of their time waiting on I/O (DB calls, downstream HTTP calls, message queues) — e.g., a Spring Boot service handling many concurrent requests where each does a few blocking DB/HTTP calls. Keeps code simple and synchronous instead of requiring a reactive rewrite.
- **Caution**: avoid `synchronized` blocks/methods on virtual-thread code paths (causes "pinning" to the carrier thread, defeating scalability) — use `ReentrantLock` instead. Virtual threads provide no benefit for CPU-bound work.
