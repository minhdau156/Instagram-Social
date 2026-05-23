# Phase 10 — Performance, Security & Polish

> **Depends on:** All previous phases  
> **BRD refs:** NFR-001 → NFR-013  
> **Branch prefix:** `chore/phase-10-` or `fix/phase-10-`

---

> **How to read this file**
> Tasks are grouped by **topic** (Performance → Security → Observability → Testing → DevOps → Documentation & Operations). Within each topic they run easiest → hardest, and every task is tagged with a **Level**:
> - **Warm-up** — a small, low-risk exercise that favours *understanding and measuring*. Do it first to build intuition for the core tasks that follow (optional but recommended).
> - **Core** — the actual Phase 10 deliverables.
> - **Stretch** — harder, senior-level work that builds on the core tasks and follows the project's hexagonal layout (`domain/` → `application/` → `adapter/` → `infrastructure/`). Tackle once the core of that topic is solid.
>
> Each task also carries:
> - **Why:** the problem you're solving — read it before you start so you understand *what* you're fixing, not just what to type.
> - **Done when:** a concrete way to prove the task works. If you can't demonstrate this, the task isn't finished.

---

## Performance

### TASK-10.1 — Establish a performance baseline with `EXPLAIN ANALYZE`
> **Level:** Warm-up · _Pairs with TASK-10.3 / TASK-10.4 / TASK-10.7_
> **Why:** Optimising without measuring is guessing. A "before" number is the only way to know whether caching or an index actually helped.
> **Done when:** You have a before/after `EXPLAIN ANALYZE` of the home-feed query saved in `docs/infra/` and can name which index (or seq scan) the planner chose.
- [ ] Run `EXPLAIN ANALYZE` on the home-feed query in `psql` and record the execution time
- [ ] Note whether the planner uses an index scan or a sequential scan
- [ ] Re-run after TASK-10.3 / 10.7 and compare the numbers

### TASK-10.2 — Tune the HikariCP connection pool
> **Level:** Warm-up · _Pairs with TASK-10.3_
> **Why:** A pool that's too large overwhelms Postgres with connections; too small and requests queue up waiting. "Bigger" is not "faster".
> **Done when:** `spring.datasource.hikari.maximum-pool-size` is set explicitly in `application.yml` with a one-line comment justifying the value.
- [ ] Read the Hikari docs on pool sizing (start around `CPU cores * 2`)
- [ ] Set `maximum-pool-size` explicitly and add a comment explaining your reasoning
- [ ] Watch the pool metrics under load via `/actuator/metrics/hikaricp.connections.active`

### TASK-10.3 — Redis caching for feed & profiles
> **Level:** Core
> **Why:** The home feed and profile pages are the most-requested endpoints in the app. Caching their results spares Postgres from re-running the same expensive query on every scroll and page visit.
> **Done when:** A second identical `GET /api/v1/feed` within 60s and `GET /api/v1/users/{username}` within 5 min are served from Redis (confirm the key exists via `redis-cli KEYS '*'`), and editing a profile removes `profile:{username}` from the cache.
- [ ] Add `spring-boot-starter-data-redis` to `pom.xml`
- [ ] Create `RedisConfig.java` in `infrastructure/config/` — configure `RedisTemplate<String, Object>` with JSON serializer
- [ ] Wrap `FeedService.getHomeFeed(cursor=null)` with a 60-second Redis cache (key: `feed:{userId}:page1`)
- [ ] Wrap `UserService.getUserProfile(username)` with a 5-minute Redis cache (key: `profile:{username}`)
- [ ] Evict profile cache on `UpdateProfileService` execution
- [ ] Add `Cache-Control: public, max-age=300` header on `GET /api/v1/users/{username}` for public profiles

### TASK-10.4 — N+1 query review
> **Level:** Core
> **Why:** When a lazy association is read inside a loop, Hibernate fires one extra query per row — the "N+1 problem". One feed request can silently become hundreds of queries.
> **Done when:** With Hibernate statistics enabled, the feed endpoint issues a small, constant number of queries regardless of how many posts come back (not "1 + number of posts").
- [ ] Audit all `@OneToMany` and `@ManyToOne` relationships — replace `FetchType.EAGER` with `FetchType.LAZY` where missing
- [ ] Add `@EntityGraph` or `JOIN FETCH` to queries that need multiple associations in one call (e.g., `PostJpaRepository` loading `PostMedia` in the feed)
- [ ] Run integration tests with Hibernate statistics enabled to verify no N+1 on feed endpoint

### TASK-10.5 — CDN-backed media URLs
> **Level:** Core
> **Why:** Serving images directly from the app server is slow and costly. A CDN caches media at edge locations close to the user, so the app server never touches image bytes after upload.
> **Done when:** A freshly uploaded image's URL is rooted at the configured CDN base URL, and the image loads in the browser through that URL.
- [ ] Update `MinioStorageAdapter.generatePresignedPutUrl` to produce URLs rooted at `VITE_CDN_BASE_URL` env var
- [ ] Add CloudFront / MinIO CDN proxy config example to `docs/` (optional: `docs/infra/cdn-setup.md`)

### TASK-10.6 — Frontend image optimization
> **Level:** Core
> **Why:** Loading every image up front wastes bandwidth and slows first paint. Lazy loading + modern formats (AVIF/WebP) + code-splitting cut the bytes a user downloads before they can interact.
> **Done when:** The browser Network tab shows off-screen images load only as you scroll, and the bundle visualizer report shows large pages split into their own chunks.
- [ ] Add `loading="lazy"` to all `<img>` tags in `PostCard`, `PostGrid`, `ProfilePage`
- [ ] Serve AVIF/WebP from backend (hint via `Accept` header handling in `MediaController`)
- [ ] Run `vite-bundle-visualizer` to identify oversized chunks; apply dynamic import (`React.lazy`) to large page components

### TASK-10.7 — Database index audit & query-plan coverage
> **Level:** Core
> **Why:** The fastest cache is a query that was already fast. Foreign keys, sort columns, and `WHERE` filters with no backing index force Postgres into sequential scans that get slower as tables grow — caching (TASK-10.3) only hides this, it doesn't fix it.
> **Done when:** `EXPLAIN ANALYZE` on the feed, follow-graph, and search queries shows index scans (not seq scans), and every foreign key used in a `JOIN` or `WHERE` has a backing index created via Flyway.
- [ ] Audit the hot queries (feed, profile, follow graph, search, notifications) with `EXPLAIN ANALYZE` and list the ones doing sequential scans
- [ ] Add a Flyway migration creating the missing indexes (FKs, `created_at` sort keys, `(user_id, created_at)` composites for cursor paging)
- [ ] Add partial indexes where useful (e.g. `WHERE deleted_at IS NULL` on `posts`)
- [ ] Re-run `EXPLAIN ANALYZE` and confirm the planner switched to index scans
- [ ] Avoid over-indexing — note any index you considered and rejected because it would hurt write throughput

### TASK-10.8 — Keyset (cursor) pagination for list endpoints
> **Level:** Core
> **Why:** `OFFSET 5000 LIMIT 20` makes Postgres read and discard 5000 rows every time, so deep pages get linearly slower and can skip/duplicate rows when data shifts underneath. Keyset pagination seeks straight to the cursor and stays O(page size).
> **Done when:** The feed, comments, followers, and notifications lists accept an opaque `cursor` instead of a page number, and fetching page 500 is as fast as page 1 (confirm via timing or `EXPLAIN`).
- [ ] Standardize a `CursorPage<T>` response shape (items + `nextCursor`) across list endpoints
- [ ] Replace `OFFSET`-based queries with keyset `WHERE (created_at, id) < (:cursorTs, :cursorId) ORDER BY created_at DESC, id DESC LIMIT :size`
- [ ] Encode/decode the cursor opaquely (base64 of the sort key) so clients don't depend on its internals
- [ ] Ensure a composite index backs the sort key (coordinate with TASK-10.7)
- [ ] Update the frontend infinite-scroll hooks to pass `nextCursor` instead of an incrementing page index

### TASK-10.9 — HTTP response compression & payload slimming
> **Level:** Core
> **Why:** Feed and profile responses are JSON-heavy; sending them uncompressed wastes bandwidth and slows mobile clients. Gzip/Brotli plus trimming unused fields cuts payload size with almost no code.
> **Done when:** Responses over ~1 KB come back with `Content-Encoding: gzip`, and the feed payload shrinks measurably (compare `Content-Length` before/after in the Network tab).
- [ ] Enable response compression in `application.yml` (`server.compression.enabled=true`, mime-types, `min-response-size`)
- [ ] Audit response DTOs for fields the client never reads; drop them or split a lighter list DTO from the detail DTO
- [ ] Confirm an `Accept-Encoding: gzip` request returns `Content-Encoding: gzip`
- [ ] (Optional) Document enabling Brotli at the nginx/CDN layer (pairs with TASK-10.5)

### TASK-10.10 — Async processing with tuned executors + virtual threads
> **Level:** Stretch
> **Why:** Long side-tasks (thumbnailing, transcode kickoff, import triggers) shouldn't block the request thread, and Java 21 virtual threads let you run many blocking-I/O tasks cheaply.
> **Done when:** A flagged endpoint returns `202 Accepted` immediately while the work runs on a background executor, and toggling virtual threads is verifiable in a thread dump (`VirtualThread` names).
- [ ] Define a named, bounded `ThreadPoolTaskExecutor` bean instead of relying on the default
- [ ] Annotate the heavy side-task method with `@Async("…")` returning `CompletableFuture<>`
- [ ] Enable virtual threads (`spring.threads.virtual.enabled=true`) and compare behaviour under load
- [ ] Make one endpoint return `202` with a status URL while the work runs async
- [ ] Confirm via thread dump / metric that the work runs off the request thread

### TASK-10.11 — Streaming large exports (CSV/ZIP, no OOM)
> **Level:** Stretch
> **Why:** Building a full data export in memory blows up the heap on large accounts — streaming writes bytes to the response as they're produced, keeping memory flat.
> **Done when:** `GET /api/v1/users/me/export` streams a CSV/ZIP of all the user's posts while heap usage stays steady (watch `/actuator/metrics/jvm.memory.used` during a large download).
- [ ] Add an endpoint returning `StreamingResponseBody` with `Content-Disposition: attachment`
- [ ] Stream rows from a cursor-based / `Stream<>` repository query — never collect the full result into a list
- [ ] Use `@Transactional(readOnly = true)` + a JDBC fetch size so Hibernate streams instead of buffering
- [ ] Build the ZIP/CSV incrementally, flushing per chunk
- [ ] Verify against a large dataset that heap stays flat throughout the download

### TASK-10.12 — Chunked / resumable large-file upload (up to 2 GB)
> **Level:** Stretch
> **Why:** A single multipart POST of a 2 GB video times out, exhausts server memory, and forces the user to restart from zero on any network blip — chunked upload streams it in parts that can resume.
> **Done when:** You can upload a 2 GB file as multiple parts, drop the connection mid-upload, resume, and the reassembled object in MinIO is byte-identical (matching checksum).
- [ ] Extend the storage out-port + `MinioStorageAdapter` to use **multipart upload** (`createMultipartUpload`, presigned `uploadPart` URLs, `completeMultipartUpload`, `abortMultipartUpload`)
- [ ] Add `MediaController` endpoints: `POST /api/v1/media/uploads` (initiate → `uploadId`), `GET .../uploads/{uploadId}/parts` (which parts exist, for resume), `POST .../uploads/{uploadId}/complete`
- [ ] Flyway migration for an `upload_session` table (uploadId, key, parts, status, created_at)
- [ ] Enforce a 2 GB total cap + a per-part size (e.g. 5–10 MB) and validate part numbers
- [ ] Abort/cleanup path for stale or cancelled sessions

### TASK-10.13 — Spring Batch: bulk import posts for a user
> **Level:** Stretch
> **Why:** Importing thousands of posts (e.g. migrating a user from another platform) in one request times out and can't recover from a mid-way failure — Spring Batch chunks the work with restart and skip handling.
> **Done when:** A job reads a CSV/JSON of N posts, writes them in chunks, and after a forced mid-job failure restarts from the last committed chunk (not from zero), visible in `BATCH_JOB_EXECUTION`.
- [ ] Add `spring-boot-starter-batch` + a Flyway migration for the Spring Batch metadata tables
- [ ] Define a chunk-oriented `Step`: `ItemReader` (CSV/JSON) → `ItemProcessor` (validate + map to the `Post` domain model) → `ItemWriter` (persist via `PostRepository`)
- [ ] Configure chunk size, a skip policy for bad rows, and retry on transient errors
- [ ] Trigger via `POST /api/v1/admin/imports/posts` (returns a job execution id) + a status endpoint
- [ ] Verify restartability: kill the job mid-run, relaunch, confirm it resumes from the last committed chunk

---

## Security

### TASK-10.14 — Add baseline security headers
> **Level:** Warm-up · _Pairs with TASK-10.20_
> **Why:** A few standard headers block whole classes of attack (MIME sniffing, clickjacking, injected scripts) for almost no effort.
> **Done when:** Responses include `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and a basic `Content-Security-Policy`, verified in the browser Network tab.
- [ ] Add the three headers in `SecurityConfig` (alongside the HSTS header from TASK-10.20)
- [ ] Confirm each header is present on a page response in DevTools → Network → Headers

### TASK-10.15 — Secrets hygiene check
> **Level:** Warm-up · _Pairs with TASK-10.16 / TASK-10.46_
> **Why:** A secret committed to git is leaked permanently — it lives in history even after you delete the line.
> **Done when:** No real secret values appear in tracked files, and the app boots reading them from environment variables.
- [ ] Search the repo for hard-coded secrets (DB password, JWT key)
- [ ] Move any found secrets to environment variables
- [ ] Add a `.env.example` with placeholder values and document it in `README.md`

### TASK-10.16 — JWT hardening
> **Level:** Core
> **Why:** Long-lived or symmetrically-signed tokens are a liability — if one leaks, the attacker has wide, lasting access. Short access tokens plus rotation shrink that window.
> **Done when:** A captured access token stops working after 15 min, and each call to `/auth/refresh` invalidates the refresh token that was used.
- [ ] Verify access token expiry = 15 min, refresh token expiry = 7 days in `JwtTokenProvider`
- [ ] Use RS256 (asymmetric) key pair instead of HS256 if not already done; store private key in env var
- [ ] Implement refresh token rotation — invalidate old token on each `/auth/refresh` call

### TASK-10.17 — Rate limiting
> **Level:** Core
> **Why:** Without limits, an attacker can brute-force passwords or flood an endpoint until it falls over. Rate limiting caps how many requests one client can make.
> **Done when:** The 11th login attempt within one minute returns `429 Too Many Requests` with a `Retry-After` header.
- [ ] Add `bucket4j-spring-boot-starter` dependency to `pom.xml`
- [ ] Configure rate limits per IP:
  - `/api/v1/auth/register` → 5 req / 10 min
  - `/api/v1/auth/login` → 10 req / 1 min
  - All other endpoints → 200 req / 1 min
- [ ] Return `429 Too Many Requests` with `Retry-After` header on limit exceeded

### TASK-10.18 — Input validation hardening
> **Level:** Core
> **Why:** Trusting client input invites bad data, injection, and stored XSS. Validating at the adapter boundary keeps the domain layer clean and the database safe.
> **Done when:** Posting an over-length caption or malformed JSON returns `400` with a clear message, and stored user text has HTML stripped.
- [ ] Audit all request DTOs — ensure every field has `@NotNull`/`@NotBlank`/`@Size`/`@Pattern` where appropriate
- [ ] Add `@ControllerAdvice` handler for `HttpMessageNotReadableException` (malformed JSON) → `400`
- [ ] Strip HTML from all user-generated text fields using OWASP AntiSamy or plain regex in a `@BeforeMapping` hook

### TASK-10.19 — OWASP dependency check in CI
> **Level:** Core
> **Why:** Most real-world breaches exploit *known* vulnerabilities in third-party libraries. Scanning dependencies on every build catches them before they ship.
> **Done when:** CI fails the build when a dependency has a CVSS ≥ 7 vulnerability, and the report is viewable in the build output.
- [ ] Add `org.owasp:dependency-check-maven` plugin to `pom.xml`
- [ ] Add `mvn dependency-check:check` step to `.github/workflows/ci.yml` (fail on CVSS ≥ 7)

### TASK-10.20 — HTTPS / TLS
> **Level:** Core
> **Why:** Plain HTTP sends tokens and passwords in the clear to anyone on the network. TLS encrypts traffic; HSTS forces browsers to always use it.
> **Done when:** Responses include a `Strict-Transport-Security` header, and behind a proxy the app reads the original scheme correctly from `X-Forwarded-Proto`.
- [ ] Document TLS termination in `docs/infra/tls-setup.md` (nginx / AWS ALB config)
- [ ] Add `server.forward-headers-strategy=FRAMEWORK` in `application-prod.yml` for `X-Forwarded-Proto` handling
- [ ] Set `Strict-Transport-Security` header in `SecurityConfig`

### TASK-10.21 — Object-level authorization & IDOR audit
> **Level:** Core
> **Why:** Authentication proves *who* you are; it doesn't stop you from editing *someone else's* post by guessing its UUID. Missing ownership checks (IDOR) are the most common real-world API vulnerability — OWASP API Security #1.
> **Done when:** Calling a mutating endpoint with another user's resource id (e.g. `DELETE /api/v1/posts/{id}` for a post you don't own) returns `403`, proven by a test, for every owner-scoped resource.
- [ ] Inventory every endpoint that mutates a user-owned resource (posts, comments, messages, profile, saved posts, follow requests)
- [ ] Verify each one checks `resource.ownerId == currentUserId()` (or admin) before acting — add the check where missing
- [ ] Return `403 Forbidden` (not `404`) for owned-resource access violations, via a named domain exception
- [ ] Add an authorization test per resource asserting the cross-user `403`
- [ ] Confirm admin/moderation endpoints require `ROLE_ADMIN` (cross-check the Phase 9 moderation work)

### TASK-10.22 — Media upload hardening
> **Level:** Core
> **Why:** Upload endpoints are a classic attack surface — a forged content-type, an oversized file, or embedded EXIF GPS data can poison storage, exhaust disk, or leak a user's location. Presigned URLs need tight scoping too.
> **Done when:** Uploading a non-image disguised as `.jpg`, a file over the size cap, or an over-resolution image is rejected, and stored images have EXIF metadata stripped.
- [ ] Validate the real content type by magic bytes (not the `Content-Type` header or extension) — allowlist `image/jpeg`, `image/png`, `image/webp`, `video/mp4`
- [ ] Enforce a max file size and (for images) max dimensions
- [ ] Strip EXIF/metadata (including GPS) from images on ingest
- [ ] Scope presigned PUT URLs tightly: short expiry, exact key, content-type and content-length conditions
- [ ] Generate stored object keys server-side (never trust a client-supplied path) to prevent overwrite/traversal

### TASK-10.23 — DAST scan in CI (OWASP ZAP baseline)
> **Level:** Core
> **Why:** TASK-10.19 scans your *dependencies* (SCA); it can't see a misconfigured header, an exposed actuator endpoint, or a reflected error message. A DAST scan probes the *running* app from the outside, the way an attacker would.
> **Done when:** CI spins up the stack, runs an OWASP ZAP baseline scan against it, and fails the build on high-risk alerts, with the HTML report saved as a build artifact.
- [ ] Add a CI job that boots the Docker Compose stack and runs the `zaproxy/zap-baseline` scan against the backend
- [ ] Tune the rule set / allowlist known-safe findings so the job is not perpetually red
- [ ] Fail the build on `High` risk alerts; warn on `Medium`
- [ ] Upload the ZAP HTML report as a CI artifact
- [ ] Document how to run the same scan locally

### TASK-10.24 — Idempotency keys for unsafe POSTs
> **Level:** Stretch
> **Why:** Flaky networks make clients retry POSTs; without idempotency a retry creates duplicate posts or messages, and a retried import could double-write.
> **Done when:** Sending the same `Idempotency-Key` twice to `POST /api/v1/posts` creates exactly one post and the second call returns the original response (verify a single DB row).
- [ ] Accept an `Idempotency-Key` header on create-post and send-message endpoints
- [ ] Flyway migration for an `idempotency_key` table (key, request hash, stored response, status, created_at)
- [ ] Add an interceptor/guard that records the key in the same transaction and short-circuits duplicates
- [ ] Return the stored response on a repeated key; `409 Conflict` if the same key arrives with a different payload
- [ ] Add a test firing the same key twice and asserting one side-effect

---

## Observability

### TASK-10.25 — Trace one request end-to-end
> **Level:** Warm-up · _Pairs with TASK-10.26_
> **Why:** Builds the mental model that makes structured logging genuinely useful — seeing one request flow through controller → service → persistence.
> **Done when:** You can paste the full, ordered set of log lines for a single "create post" request, all sharing one `requestId`.
- [ ] Trigger one `POST /api/v1/posts` request
- [ ] Grep the logs for its `requestId`
- [ ] Confirm the lines appear in controller → service → adapter order

### TASK-10.26 — Structured logging & MDC
> **Level:** Core
> **Why:** Free-text logs are hard to search and correlate. JSON logs with a per-request `requestId` let you follow a single user's request across many log lines and machines.
> **Done when:** Every log line produced by one request shares the same `requestId`, and logs are valid JSON in non-local profiles.
- [ ] Follow the `logging-patterns` skill instructions
- [ ] Create `MdcLoggingFilter.java` — sets `requestId`, `userId`, `method`, `path` in MDC for every request
- [ ] Update `logback-spring.xml` to output JSON format in non-local profiles
- [ ] Replace any remaining `System.out.println` with SLF4J calls

### TASK-10.27 — Actuator & Micrometer
> **Level:** Core
> **Why:** You can't operate what you can't see. Health and metrics endpoints expose the app's internal state so monitoring tools (and you) can spot trouble early.
> **Done when:** `/actuator/health` returns `UP`, `/actuator/prometheus` lists your custom counters, and those counters increment when you create a post / like / register.
- [ ] Add `spring-boot-starter-actuator` + `micrometer-registry-prometheus` to `pom.xml`
- [ ] Expose `health`, `info`, `metrics`, `prometheus` endpoints
- [ ] Add custom `MeterRegistry` counter for post creations, likes, registrations
- [ ] Secure Actuator endpoints (allow only `ROLE_ADMIN` except `/actuator/health`)

### TASK-10.28 — Distributed tracing
> **Level:** Core
> **Why:** When a request crosses several services, a trace shows exactly where time was spent and which hop failed — far faster than reading each service's logs separately.
> **Done when:** A request shows up as a single trace with timed spans in the Zipkin UI.
- [ ] Add `io.micrometer:micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin` to `pom.xml`
- [ ] Configure `application.yml` → `management.tracing.sampling.probability=1.0` for dev
- [ ] Run Zipkin locally via Docker Compose (`openzipkin/zipkin` image)

### TASK-10.29 — Grafana dashboards & Prometheus alerting
> **Level:** Core · _Builds on TASK-10.27_
> **Why:** TASK-10.27 made Prometheus *collect* metrics, but nothing graphs them and nobody gets told when they go bad. A dashboard turns scattered counters into an at-a-glance picture; alert rules turn "the graph looks wrong" into an actual page.
> **Done when:** A Grafana dashboard renders request rate, 5xx error rate, and p95 latency from your Micrometer metrics, and an alert moves to `firing` (visible in Alertmanager / Grafana) when you drive the 5xx rate past its threshold.
- [ ] Add `prometheus`, `grafana`, and `alertmanager` services to `docker-compose.yml`; point Prometheus at `/actuator/prometheus`
- [ ] Provision a Grafana dashboard (JSON under `docs/infra/grafana/`) with panels: request rate, 5xx error rate, p95/p99 latency (from `http_server_requests`), JVM heap, HikariCP active connections
- [ ] Define Prometheus alert rules in `prometheus/alerts.yml`: high 5xx rate, high p99 latency, `instance down`
- [ ] Wire Alertmanager (or Grafana alerting) to a notification channel (Slack webhook / email) — placeholder receiver config is fine
- [ ] Document how to open Grafana and import/verify the dashboard in `docs/infra/`

### TASK-10.30 — Centralized log aggregation (Loki + Promtail)
> **Level:** Core · _Builds on TASK-10.26_
> **Why:** TASK-10.26 produces structured JSON logs, but they're stranded in each container's stdout. Shipping them to Loki lets you search by `requestId` / `userId` across every instance and run — which is the entire payoff of structured logging in the first place.
> **Done when:** Backend logs appear in Grafana → Explore (Loki datasource) and you can filter to a single request's full lifecycle by its `requestId`.
- [ ] Add `loki` + `promtail` services to `docker-compose.yml` (Promtail tails the container logs)
- [ ] Configure Promtail to parse the JSON format from `logback-spring.xml` and promote `requestId`, `userId`, and `level` to labels (keep label cardinality low — no per-request labels)
- [ ] Add Loki as a provisioned Grafana datasource
- [ ] Verify a `{app="backend"} | json | requestId="…"` query returns the controller → service → adapter lines for one request
- [ ] (Optional) Link logs ↔ traces: derive a Grafana field from `traceId` (TASK-10.28) that jumps to the Zipkin span

### TASK-10.31 — SLIs, SLOs & error-budget burn-rate alerts
> **Level:** Core · _Builds on TASK-10.27 / TASK-10.29_
> **Why:** Raw metrics tell you *what* is happening, not whether it's *acceptable*. An SLO sets an explicit reliability target; the error budget converts it into a spend you can burn, and multi-window burn-rate alerts page you while there's still budget left to defend.
> **Done when:** A documented SLO (e.g. "99.5% of `GET /api/v1/feed` succeed under 300 ms over 30 days") is tracked on a Grafana panel showing the remaining error budget, and a fast-burn alert fires when the budget drains too quickly.
- [ ] Define SLIs from `http_server_requests`: availability (non-5xx ratio) and latency (fraction under threshold) for the feed, profile, and login endpoints
- [ ] Document SLO targets, windows, and rationale in `docs/infra/slo.md`
- [ ] Add Prometheus recording rules computing the SLI ratios and the 30-day error budget
- [ ] Add multi-window, multi-burn-rate alert rules (fast burn: 1h + 5m; slow burn: 6h + 30m)
- [ ] Add a Grafana SLO panel: current SLI, target line, remaining error budget

### TASK-10.32 — Frontend RUM & end-to-end error tracking (Sentry)
> **Level:** Core · _Builds on TASK-10.28_
> **Why:** Server metrics are blind to the client — a broken bundle, a slow render, or an uncaught JS exception never reaches the backend. RUM captures real users' Core Web Vitals and error tracking captures stack traces from both the React app and the Spring API in one place.
> **Done when:** A thrown error in the React app appears in the error-tracking dashboard with a source-mapped stack trace, Core Web Vitals (LCP/INP/CLS) are reported for real page loads, and a backend exception lands in the same tool correlated by trace/release.
- [ ] Add error tracking — `@sentry/react` in the frontend, `sentry-spring-boot-starter` in the backend; DSNs via env vars (`VITE_SENTRY_DSN`, `SENTRY_DSN`)
- [ ] Initialize Sentry in the React app and integrate it with the existing `ErrorBoundary`; upload source maps from the frontend build (TASK-10.45 / CI)
- [ ] Report Core Web Vitals (LCP, INP, CLS) via the `web-vitals` package
- [ ] Propagate `traceId` (TASK-10.28) from frontend → backend so a failed request links the browser event to the server span
- [ ] Scrub PII (tokens, emails, message bodies) in `beforeSend`; document per-environment sampling rates

---

## Testing

### TASK-10.33 — Swap one integration test to Testcontainers
> **Level:** Warm-up · _Pairs with TASK-10.35 / TASK-10.39_
> **Why:** H2 doesn't behave exactly like Postgres (e.g. `ILIKE`, full-text search). A test can pass against H2 but break in production — Testcontainers runs the real database.
> **Done when:** One existing `@DataJpaTest` (e.g. `SearchJpaAdapterIT`) spins up a real Postgres container and passes using genuine Postgres SQL features.
- [ ] Add the Testcontainers + Postgres dependencies (test scope)
- [ ] Convert one IT to use a `@Container PostgreSQLContainer`
- [ ] Confirm the test passes against real Postgres, not H2

### TASK-10.34 — Cover one untested branch
> **Level:** Warm-up · _Pairs with TASK-10.35_
> **Why:** Practises the core skill behind the coverage gate: reading a report, finding a gap, and writing the targeted test that closes it.
> **Done when:** A previously red (uncovered) exception branch shows as covered in the JaCoCo report after your new test.
- [ ] Open the JaCoCo HTML report and find one uncovered exception branch
- [ ] Write a test that triggers that branch
- [ ] Re-run and confirm the branch is now covered

### TASK-10.35 — Backend test coverage gate
> **Level:** Core
> **Why:** A coverage gate stops new untested code from sneaking into the codebase and makes "did we test the error paths?" an automatic check instead of a hope.
> **Done when:** `mvn verify` fails when coverage drops below 80%, and the JaCoCo report shows exception-handler branches as covered.
- [ ] Add `jacoco-maven-plugin` to `pom.xml`, fail build if coverage < 80%
- [ ] Identify and fill test gaps from phases 1–9 (prioritize domain services and controllers)
- [ ] Ensure all exception handler branches are covered

### TASK-10.36 — Frontend component tests
> **Level:** Core
> **Why:** Component tests catch UI regressions — broken validation, a like toggle that stops working — without you manually clicking through the app every time.
> **Done when:** `npm test` runs green and the listed components pass tests covering their error and optimistic-update states, with API calls mocked by MSW.
- [ ] Add `vitest` + `@testing-library/react` + `msw` (mock service worker) to `package.json`
- [ ] Write tests for:
  - `LoginPage` — form validation, submit calls API, error state
  - `PostCard` — renders media, like/save toggles, comment count
  - `LikeButton` — optimistic update
  - `ProtectedRoute` — redirects unauthenticated users
  - `useWebSocket` hook — subscription + message handling

### TASK-10.37 — E2E smoke tests (Playwright)
> **Level:** Core
> **Why:** Every unit test can pass while the wired-together app is broken (bad routing, CORS, env config). E2E tests exercise the real user journeys through a real browser.
> **Done when:** `npx playwright test` drives the full register → login → post → like flow against the running stack and passes.
- [ ] Add `@playwright/test` to `package.json`
- [ ] Create `e2e/` directory with tests:
  - `auth.spec.ts` — register → login → view profile
  - `posts.spec.ts` — create post → view in feed → like → comment
  - `follow.spec.ts` — follow user → see posts in feed → unfollow
  - `messaging.spec.ts` — open DM → send message → verify delivery
- [ ] Add Playwright step to CI (run against the Docker Compose stack)

### TASK-10.38 — Architecture fitness tests (ArchUnit)
> **Level:** Core
> **Why:** The hexagonal layering rule (`domain` depends on nothing; adapters depend inward) is enforced today only by discipline and code review. ArchUnit turns "don't import Spring in the domain" into a failing test, so the architecture can't quietly erode.
> **Done when:** `mvn test` fails if anyone adds a Spring/JPA/Lombok import to `domain/`, has a controller call a persistence adapter directly, or lets the domain depend on `adapter`/`infrastructure`.
- [ ] Add the `com.tngtech.archunit:archunit-junit5` dependency (test scope)
- [ ] Rule: no class in `domain..` imports `org.springframework..`, `jakarta.persistence..`, or `lombok..`
- [ ] Rule: dependencies only point inward (`domain` ← `application` ← `adapter`/`infrastructure`); no outward edges from the domain
- [ ] Rule: naming conventions hold (`*Controller` in `adapter.in.web`, `*JpaEntity` only in `persistence`, `*UseCase` interfaces in `domain.port.in`)
- [ ] Rule: controllers depend on use-case in-ports, never on persistence adapters or `*JpaRepository`

### TASK-10.39 — Testcontainers for the integration suite
> **Level:** Core
> **Why:** H2 silently diverges from Postgres — `ILIKE`, full-text search, `citext`, `uuid_generate_v4()`, and partial indexes behave differently or not at all. Tests that pass on H2 can mask bugs that only surface in production. Testcontainers runs the real Postgres.
> **Done when:** All `*IT` persistence/integration tests run against a real Postgres container (with the project's extensions and Flyway migrations applied) and pass in CI.
- [ ] Add Testcontainers (`postgresql`, `junit-jupiter`) at test scope
- [ ] Create a shared `@Container PostgreSQLContainer` base class (singleton/reused container) with Flyway migrations applied
- [ ] Migrate the `@DataJpaTest` ITs off H2 onto the container; enable `pg_trgm` / `citext` so FTS and case-insensitive tests are real
- [ ] Ensure CI provides Docker for the Testcontainers run
- [ ] Remove the H2 test dependency/config once nothing depends on it

### TASK-10.40 — Load & soak testing (k6)
> **Level:** Core
> **Why:** Functional tests prove correctness at one request; they say nothing about behaviour at 500 concurrent users. Load testing finds the breaking point and validates the SLOs (TASK-10.31) before real traffic does.
> **Done when:** A k6 script drives the feed/login/post-create journey at a target RPS, reports p95/p99 latency and error rate, and a run fails if p95 exceeds the SLO threshold.
- [ ] Add a `k6/` directory with scripts for the hottest journeys (login, home-feed scroll, create post)
- [ ] Define stages (ramp-up → steady → spike) and thresholds tied to the SLOs from TASK-10.31
- [ ] Run against the Docker Compose stack and capture p95/p99 + error rate
- [ ] Add a soak scenario (sustained load for N minutes) to surface memory leaks / connection-pool exhaustion
- [ ] Document baseline numbers in `docs/infra/load-test.md` and fail the run on a threshold breach

---

## DevOps

### TASK-10.41 — Add `.dockerignore` files
> **Level:** Warm-up · _Pairs with TASK-10.44 / TASK-10.45_
> **Why:** Without it, Docker copies `target/`, `node_modules/`, and `.git/` into the build context — slowing builds and bloating images.
> **Done when:** `docker build` no longer copies those directories and the build context size visibly drops.
- [ ] Add `backend/.dockerignore` (ignore `target/`, `.git/`, `*.md`)
- [ ] Add `frontend/.dockerignore` (ignore `node_modules/`, `dist/`, `.git/`)
- [ ] Re-run `docker build` and confirm the "transferring context" size is smaller

### TASK-10.42 — Write a one-command smoke test script
> **Level:** Warm-up · _Pairs with TASK-10.46_
> **Why:** A fast "is it alive?" check saves you from clicking around the UI to confirm a deploy didn't break the basics.
> **Done when:** Running the script against the running stack prints a clear pass/fail for each check.
- [ ] Create a `make smoke` target (or `scripts/smoke.sh`)
- [ ] Have it curl `/actuator/health` and one real endpoint (e.g. `GET /api/v1/feed`)
- [ ] Exit non-zero if any check fails

### TASK-10.43 — Write your first ADR (Architecture Decision Record)
> **Level:** Warm-up · _Pairs with any decision in this phase_
> **Why:** ADRs capture *why* a choice was made, so future-you and teammates don't re-argue settled decisions.
> **Done when:** A short ADR exists at `docs/adr/0001-*.md` with **Context**, **Decision**, and **Consequences** sections.
- [ ] Pick one Phase 10 decision (e.g. "RS256 over HS256" or "Redis for feed cache")
- [ ] Write it up in `docs/adr/0001-<slug>.md` using the Context / Decision / Consequences format
- [ ] Link it from `docs/` so it's discoverable

### TASK-10.44 — Dockerfile: backend
> **Level:** Core
> **Why:** A multi-stage build keeps the final image small (JRE only, no Maven or source), which ships faster and exposes less attack surface.
> **Done when:** `docker build` produces an image that boots the API and responds on port 8080.
- [ ] Create `backend/Dockerfile` (multi-stage):
  - Stage 1 (`build`): `maven:3.9-eclipse-temurin-21` — `mvn package -DskipTests`
  - Stage 2 (`run`): `eclipse-temurin:21-jre-alpine` — copy JAR, `EXPOSE 8080`, `ENTRYPOINT`

### TASK-10.45 — Dockerfile: frontend
> **Level:** Core
> **Why:** The React build output is just static files; serving them with nginx is far lighter than running Node in production. SPA fallback makes client-side routes survive a page refresh.
> **Done when:** The container serves the app and refreshing a deep link (e.g. `/search`) returns the app, not a 404.
- [ ] Create `frontend/Dockerfile` (multi-stage):
  - Stage 1 (`build`): `node:20-alpine` — `npm ci && npm run build`
  - Stage 2 (`serve`): `nginx:alpine` — copy `dist/`, configure SPA fallback in `nginx.conf`

### TASK-10.46 — Full docker-compose.yml
> **Level:** Core
> **Why:** One command should bring up the entire stack so any teammate can run an identical environment. Healthchecks ensure dependent services don't start before their dependencies are ready.
> **Done when:** `docker compose up` brings all services to a healthy state and the frontend talks to the backend end-to-end.
- [ ] Update `docker-compose.yml` to include all services:
  - `backend` (depends on `postgres`, `redis`)
  - `frontend` (depends on `backend`)
  - `postgres`, `minio`, `redis`, `zipkin`
- [ ] Add `healthcheck` blocks for all services
- [ ] Document startup order and port mapping in `README.md`

### TASK-10.47 — CI/CD: Docker build & push
> **Level:** Core
> **Why:** Automating image build + push means every merge to main produces a deployable, versioned artifact with zero manual steps — and you can always trace an image back to its commit.
> **Done when:** A push to main produces images in GHCR tagged with both the commit SHA and `latest`.
- [ ] Extend `.github/workflows/ci.yml`:
  - After tests pass: `docker build` backend + frontend images
  - Push to GitHub Container Registry (`ghcr.io`)
  - Tag with `sha:${{ github.sha }}` and `latest` on main branch pushes

### TASK-10.48 — Scheduled jobs with distributed locking (ShedLock)
> **Level:** Stretch
> **Why:** `@Scheduled` jobs fire on every instance, so 3 replicas run a cleanup job 3× at once — a distributed lock guarantees exactly one instance runs each tick.
> **Done when:** With two app instances running, a scheduled job executes on only one instance per tick (verify via logs and the lock row).
- [ ] Add `shedlock-spring` + a JDBC lock provider and a Flyway migration for the `shedlock` table
- [ ] Create `@Scheduled` jobs: purge orphaned media, expire stale multipart-upload sessions (TASK-10.12), delete old idempotency keys (TASK-10.24)
- [ ] Wrap each job with `@SchedulerLock(name, lockAtMostFor)`
- [ ] Run two instances locally and confirm the job runs once per tick, not twice
- [ ] Emit a log line / metric per job run for auditability

---

## Documentation & Operations

### TASK-10.49 — Troubleshooting runbook for common failures
> **Level:** Warm-up · _Pairs with TASK-10.42 / TASK-10.46_
> **Why:** When the stack won't boot, people rediscover the same handful of fixes over and over. A short runbook turns that tribal knowledge into a lookup table anyone can scan.
> **Done when:** `docs/troubleshooting.md` lists the most common local-dev and runtime failures, each written as **Symptom → Cause → Fix**, and is linked from the README.
- [ ] Create `docs/troubleshooting.md` with a **Symptom → Cause → Fix** table
- [ ] Cover at least: port already in use (`8080`/`5432`/`9000`), Postgres "connection refused" on boot, Flyway checksum mismatch, missing JWT/OAuth env vars, CORS error from the frontend, MinIO bucket not created
- [ ] Note the one-liner that checks each service is healthy (reuse the `make smoke` check from TASK-10.42)
- [ ] Link the runbook from `README.md`

### TASK-10.50 — Architecture & flow diagrams (Mermaid)
> **Level:** Core
> **Why:** The hexagonal layering and request flows live only in prose today. Mermaid diagrams make the structure graspable at a glance and, because they're plain text, they version and diff in git like code.
> **Done when:** `docs/diagrams.md` renders Mermaid diagrams for the system architecture, one request sequence, and the core data model — and they all render in GitHub's markdown preview without syntax errors.
- [ ] Add a `flowchart` of the hexagonal layers (`infrastructure → adapter → application → domain`) with dependency arrows pointing inward
- [ ] Add a `sequenceDiagram` for one end-to-end request (e.g. create post: `Controller → UseCase → PersistenceAdapter → Postgres`, including the MinIO presign hop)
- [ ] Add an `erDiagram` for the core tables (`users`, `posts`, `comments`, `likes`, `follows`) with relationships and key columns sourced from `docs/database/schema.sql`
- [ ] (Optional) Add a deployment `flowchart` of the Docker Compose services (TASK-10.46): frontend, backend, postgres, redis, minio, zipkin
- [ ] Confirm every diagram renders in the GitHub markdown preview and link `docs/diagrams.md` from the README

### TASK-10.51 — Diagnose an `OutOfMemoryError` from a heap dump (hands-on lab)
> **Level:** Warm-up · _Pairs with TASK-10.11 / TASK-10.27 / TASK-10.49_
> **Why:** OOM crashes are confusing for beginners because the stack trace blames whatever allocation happened to fail *last* — not the code that actually leaked the memory. The only reliable way to find the real culprit is a heap dump, and the only way to get comfortable reading one is to trigger an OOM on purpose in a safe place.
> **Done when:** You can reproduce an `OutOfMemoryError: Java heap space` on a throwaway endpoint, open the auto-generated `.hprof` in a heap analyzer, and name the object type holding the most retained memory.
- [ ] Start the app with a small heap and auto-dump on crash: `-Xmx256m -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof`
- [ ] Add a temporary `/dev/oom` endpoint that appends to an in-memory `List<byte[]>` in a loop until the heap is exhausted — **delete it after the exercise** (it must never ship)
- [ ] Watch `/actuator/metrics/jvm.memory.used` (TASK-10.27) climb toward the max right before the crash
- [ ] Open the generated `heapdump.hprof` in Eclipse MAT (or VisualVM) and use the **Dominator Tree** / **Leak Suspects** report to find the object retaining the most memory
- [ ] Note the difference between `OutOfMemoryError: Java heap space` and `GC overhead limit exceeded`, and decide which points to a real leak vs. a heap that's simply too small
- [ ] Add a short "How to read a heap dump" entry to `docs/troubleshooting.md` (TASK-10.49): how to enable the dump, where it lands, and how to open the dominator tree

### TASK-10.52 — Logging best practices audit
> **Level:** Warm-up · _Pairs with TASK-10.25 / TASK-10.26 / TASK-10.49_
> **Why:** In production you can't attach a debugger — logs are the only window into what went wrong. But logs only help if they carry the right level, the right context, and the full stack trace. Bad logging (swallowed exceptions, string concatenation, secrets in plain text) actively *slows* incident response and can leak user data.
> **Done when:** A short logging-conventions guide exists, and an audit of the codebase has fixed the worst offenders (string concatenation, missing stack traces, logged secrets) — verifiable by grepping for the anti-patterns and finding none left.
- [ ] Follow the `logging-patterns` skill and write a short "logging conventions" note (in `CONTRIBUTING.md` or `docs/`): when to use `ERROR` / `WARN` / `INFO` / `DEBUG` / `TRACE`
- [ ] Replace string concatenation with parameterized logging — `log.info("post {} by user {}", postId, userId)`, never `log.info("post " + postId)`
- [ ] Ensure every `catch` that logs passes the exception as the **last argument** (`log.error("create post failed", ex)`) so the stack trace is preserved — never `log.error(ex.getMessage())`
- [ ] Confirm no secret or PII is ever logged (passwords, JWT/refresh tokens, emails, message bodies); redact where found
- [ ] Attach correlation context to boundary logs (the `requestId` / `userId` from MDC in TASK-10.26) and remove any logging inside hot loops
- [ ] Verify nothing logs-and-rethrows (the same error logged twice as it bubbles up the stack)
