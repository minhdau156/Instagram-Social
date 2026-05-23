# Phase 13 — Resilience & Fault Tolerance

> **Track:** Backend · **Depends on:** Phases 6, 10, 11 · **New tools:** Resilience4j, Spring Cloud Circuit Breaker  
> **Branch prefix:** `feat/phase-13-`

---

> **Skills you'll build:**
> - Timeouts, retries with exponential backoff + jitter
> - Circuit breakers and half-open recovery
> - Bulkheads (isolating thread pools so one slow dependency can't sink everything)
> - Graceful degradation & fallbacks
> - Basic chaos testing
>
> **Best practices:** every network call gets a timeout; retry only idempotent operations; fail fast with a fallback rather than hanging; reflect degraded dependencies in `/actuator/health`.

---

> **How to read this file**
> - **Why:** the problem this task solves — read it before you start.
> - **Done when:** a concrete way to prove the task works. If you can't demonstrate it, it isn't finished.

---

## Setup

### TASK-13.1 — Add Resilience4j + actuator integration
> **Why:** Resilience4j provides the building blocks (circuit breaker, retry, bulkhead, timeout) and its actuator integration makes their state observable.
> **Done when:** The app boots with Resilience4j on the classpath and `/actuator/health` shows the circuit-breaker health indicator.
- [ ] Add `resilience4j-spring-boot3` (and `resilience4j-micrometer`) to `backend/pom.xml`
- [ ] Add base instance config under `resilience4j.*` in `application.yml`
- [ ] Enable the circuit-breaker health indicator via `management.health.circuitbreakers.enabled=true`
- [ ] Verify the indicator appears in `/actuator/health`

---

## Outbound Protection

### TASK-13.2 — Timeouts on all outbound calls (MinIO, OAuth, Kafka)
> **Why:** A network call with no timeout can hang a request thread forever; explicit timeouts make slow dependencies fail fast.
> **Done when:** Every outbound adapter (storage, OAuth, broker) has an explicit connect/read timeout, demonstrated by a call returning an error promptly when the dependency is unreachable.
- [ ] Set connect/read timeouts on the MinIO client in `infrastructure/storage/` (and the `MinioStorageAdapter`)
- [ ] Set timeouts on the OAuth2 / outbound HTTP client in `infrastructure/security/` or `infrastructure/config/`
- [ ] Set producer/consumer request timeouts for Kafka in `application.yml`
- [ ] Verify a call to an unreachable dependency errors within the configured timeout (not indefinitely)

### TASK-13.3 — Circuit breaker around media storage, with a fallback
> **Why:** When MinIO/S3 is down, repeatedly calling it makes things worse; a breaker trips after failures and a fallback keeps the API responsive.
> **Done when:** With storage stopped, the breaker opens and the media call returns a defined fallback (e.g. a queued-upload or clear error) instead of hanging.
- [ ] Annotate the storage call in `adapter/out/persistence`/`infrastructure/storage` with `@CircuitBreaker(name = "mediaStorage", fallbackMethod = ...)`
- [ ] Implement a fallback method returning a graceful response
- [ ] Tune failure-rate threshold and wait-duration in `application.yml`
- [ ] Verify the breaker transitions closed -> open -> half-open with the dependency down then up

### TASK-13.4 — Retry with backoff on transient DB/broker errors
> **Why:** Transient blips (a brief connection reset) succeed on a second attempt; retry with exponential backoff + jitter rides them out without hammering the dependency.
> **Done when:** A transient failure is retried with increasing delay and ultimately succeeds, while a non-idempotent operation is *not* retried.
- [ ] Apply `@Retry(name = ...)` to idempotent broker publish / read operations
- [ ] Configure exponential backoff + jitter and a max-attempts cap in `application.yml`
- [ ] Ensure non-idempotent commands are excluded from retry
- [ ] Add a test that fails N-1 times then succeeds, asserting the call count

---

## Isolation & Degradation

### TASK-13.5 — Bulkhead isolating the search/feed thread pools
> **Why:** Without isolation, a slow search query can exhaust the shared thread pool and take down unrelated endpoints; bulkheads cap each dependency's concurrency.
> **Done when:** Saturating the search bulkhead rejects/queues only search calls while the feed endpoint stays responsive.
- [ ] Apply `@Bulkhead` (or thread-pool bulkhead) to the search and feed query paths
- [ ] Configure max concurrent calls / pool size per bulkhead in `application.yml`
- [ ] Verify other endpoints respond normally while one bulkhead is saturated
- [ ] Expose bulkhead metrics for inspection

### TASK-13.6 — Cache-based feed fallback when the DB is degraded
> **Why:** Serving a slightly stale cached feed beats showing an error when the DB is slow or down — graceful degradation keeps the app usable.
> **Done when:** With the feed DB query failing, `GET /api/v1/feed` returns the last cached feed instead of a 5xx.
- [ ] Add a fallback method on the feed query that reads from the Redis cache (from Phase 10)
- [ ] Wire it as the `@CircuitBreaker` fallback for the feed read path
- [ ] Mark the response as degraded (header or flag) so the UI can indicate staleness
- [ ] Verify: induce a feed DB failure and confirm a cached feed is returned

---

## Chaos & Observability

### TASK-13.7 — Chaos test: kill Redis/MinIO mid-test, assert graceful degradation
> **Why:** Resilience config is only real if proven under failure; a chaos test stops a dependency and asserts the app degrades instead of crashing.
> **Done when:** An integration test stops a containerized dependency mid-run and asserts the relevant endpoint returns a fallback / clear error, not a hang or 500 storm.
- [ ] Add an IT (Testcontainers) that boots the app with Redis/MinIO containers
- [ ] Stop the container mid-test (`container.stop()`) and call the dependent endpoint
- [ ] Assert the fallback path (cached feed / graceful error) and that the breaker opened
- [ ] Restart the dependency and assert recovery (breaker closes)

### TASK-13.8 — Expose circuit-breaker state to Prometheus
> **Why:** Operators need to see breaker state and failure rates to alert before users notice; exporting to Prometheus makes that possible.
> **Done when:** `/actuator/prometheus` exposes `resilience4j_circuitbreaker_state` (and call metrics) and the values change as a breaker opens/closes.
- [ ] Ensure `resilience4j-micrometer` is registered (from TASK-13.1) so breaker/retry/bulkhead metrics are emitted
- [ ] Confirm the metrics appear at `/actuator/prometheus`
- [ ] Add a basic Grafana panel or alert rule note in `docs/` for the open-state metric
- [ ] Verify a metric value flips when a breaker opens during the TASK-13.7 chaos test
