# Phase 10 — Performance, Security & Polish

> **Depends on:** All previous phases  
> **BRD refs:** NFR-001 → NFR-013  
> **Branch prefix:** `chore/phase-10-` or `fix/phase-10-`

---

> **How to read this file**
> Each task carries two extra lines for learning:
> - **Why:** the reason this task matters — read it before you start so you understand *what problem you're solving*, not just what to type.
> - **Done when:** a concrete way to prove the task works. If you can't demonstrate this, the task isn't finished.
>
> Tasks **10.1 – 10.19** are the core phase work.
> Tasks **10.20 – 10.29** are gentler *learning warm-ups* — optional but recommended for building intuition. Each pairs with a core task.
> Tasks **10.30 – 10.35** are *advanced backend* stretch tasks (large/chunked uploads, Spring Batch, async + virtual threads, idempotency, streaming exports, distributed scheduling) for going deeper on the backend.

---

## Performance

### TASK-10.1 — Redis caching for feed & profiles
> **Why:** The home feed and profile pages are the most-requested endpoints in the app. Caching their results spares Postgres from re-running the same expensive query on every scroll and page visit.
> **Done when:** A second identical `GET /api/v1/feed` within 60s and `GET /api/v1/users/{username}` within 5 min are served from Redis (confirm the key exists via `redis-cli KEYS '*'`), and editing a profile removes `profile:{username}` from the cache.
- [ ] Add `spring-boot-starter-data-redis` to `pom.xml`
- [ ] Create `RedisConfig.java` in `infrastructure/config/` — configure `RedisTemplate<String, Object>` with JSON serializer
- [ ] Wrap `FeedService.getHomeFeed(cursor=null)` with a 60-second Redis cache (key: `feed:{userId}:page1`)
- [ ] Wrap `UserService.getUserProfile(username)` with a 5-minute Redis cache (key: `profile:{username}`)
- [ ] Evict profile cache on `UpdateProfileService` execution
- [ ] Add `Cache-Control: public, max-age=300` header on `GET /api/v1/users/{username}` for public profiles

### TASK-10.2 — N+1 query review
> **Why:** When a lazy association is read inside a loop, Hibernate fires one extra query per row — the "N+1 problem". One feed request can silently become hundreds of queries.
> **Done when:** With Hibernate statistics enabled, the feed endpoint issues a small, constant number of queries regardless of how many posts come back (not "1 + number of posts").
- [ ] Audit all `@OneToMany` and `@ManyToOne` relationships — replace `FetchType.EAGER` with `FetchType.LAZY` where missing
- [ ] Add `@EntityGraph` or `JOIN FETCH` to queries that need multiple associations in one call (e.g., `PostJpaRepository` loading `PostMedia` in the feed)
- [ ] Run integration tests with Hibernate statistics enabled to verify no N+1 on feed endpoint

### TASK-10.3 — CDN-backed media URLs
> **Why:** Serving images directly from the app server is slow and costly. A CDN caches media at edge locations close to the user, so the app server never touches image bytes after upload.
> **Done when:** A freshly uploaded image's URL is rooted at the configured CDN base URL, and the image loads in the browser through that URL.
- [ ] Update `MinioStorageAdapter.generatePresignedPutUrl` to produce URLs rooted at `VITE_CDN_BASE_URL` env var
- [ ] Add CloudFront / MinIO CDN proxy config example to `docs/` (optional: `docs/infra/cdn-setup.md`)

### TASK-10.4 — Frontend image optimization
> **Why:** Loading every image up front wastes bandwidth and slows first paint. Lazy loading + modern formats (AVIF/WebP) + code-splitting cut the bytes a user downloads before they can interact.
> **Done when:** The browser Network tab shows off-screen images load only as you scroll, and the bundle visualizer report shows large pages split into their own chunks.
- [ ] Add `loading="lazy"` to all `<img>` tags in `PostCard`, `PostGrid`, `ProfilePage`
- [ ] Serve AVIF/WebP from backend (hint via `Accept` header handling in `MediaController`)
- [ ] Run `vite-bundle-visualizer` to identify oversized chunks; apply dynamic import (`React.lazy`) to large page components

---

## Security

### TASK-10.5 — JWT hardening
> **Why:** Long-lived or symmetrically-signed tokens are a liability — if one leaks, the attacker has wide, lasting access. Short access tokens plus rotation shrink that window.
> **Done when:** A captured access token stops working after 15 min, and each call to `/auth/refresh` invalidates the refresh token that was used.
- [ ] Verify access token expiry = 15 min, refresh token expiry = 7 days in `JwtTokenProvider`
- [ ] Use RS256 (asymmetric) key pair instead of HS256 if not already done; store private key in env var
- [ ] Implement refresh token rotation — invalidate old token on each `/auth/refresh` call

### TASK-10.6 — Rate limiting
> **Why:** Without limits, an attacker can brute-force passwords or flood an endpoint until it falls over. Rate limiting caps how many requests one client can make.
> **Done when:** The 11th login attempt within one minute returns `429 Too Many Requests` with a `Retry-After` header.
- [ ] Add `bucket4j-spring-boot-starter` dependency to `pom.xml`
- [ ] Configure rate limits per IP:
  - `/api/v1/auth/register` → 5 req / 10 min
  - `/api/v1/auth/login` → 10 req / 1 min
  - All other endpoints → 200 req / 1 min
- [ ] Return `429 Too Many Requests` with `Retry-After` header on limit exceeded

### TASK-10.7 — Input validation hardening
> **Why:** Trusting client input invites bad data, injection, and stored XSS. Validating at the adapter boundary keeps the domain layer clean and the database safe.
> **Done when:** Posting an over-length caption or malformed JSON returns `400` with a clear message, and stored user text has HTML stripped.
- [ ] Audit all request DTOs — ensure every field has `@NotNull`/`@NotBlank`/`@Size`/`@Pattern` where appropriate
- [ ] Add `@ControllerAdvice` handler for `HttpMessageNotReadableException` (malformed JSON) → `400`
- [ ] Strip HTML from all user-generated text fields using OWASP AntiSamy or plain regex in a `@BeforeMapping` hook

### TASK-10.8 — OWASP dependency check in CI
> **Why:** Most real-world breaches exploit *known* vulnerabilities in third-party libraries. Scanning dependencies on every build catches them before they ship.
> **Done when:** CI fails the build when a dependency has a CVSS ≥ 7 vulnerability, and the report is viewable in the build output.
- [ ] Add `org.owasp:dependency-check-maven` plugin to `pom.xml`
- [ ] Add `mvn dependency-check:check` step to `.github/workflows/ci.yml` (fail on CVSS ≥ 7)

### TASK-10.9 — HTTPS / TLS
> **Why:** Plain HTTP sends tokens and passwords in the clear to anyone on the network. TLS encrypts traffic; HSTS forces browsers to always use it.
> **Done when:** Responses include a `Strict-Transport-Security` header, and behind a proxy the app reads the original scheme correctly from `X-Forwarded-Proto`.
- [ ] Document TLS termination in `docs/infra/tls-setup.md` (nginx / AWS ALB config)
- [ ] Add `server.forward-headers-strategy=FRAMEWORK` in `application-prod.yml` for `X-Forwarded-Proto` handling
- [ ] Set `Strict-Transport-Security` header in `SecurityConfig`

---

## Observability

### TASK-10.10 — Structured logging & MDC
> **Why:** Free-text logs are hard to search and correlate. JSON logs with a per-request `requestId` let you follow a single user's request across many log lines and machines.
> **Done when:** Every log line produced by one request shares the same `requestId`, and logs are valid JSON in non-local profiles.
- [ ] Follow the `logging-patterns` skill instructions
- [ ] Create `MdcLoggingFilter.java` — sets `requestId`, `userId`, `method`, `path` in MDC for every request
- [ ] Update `logback-spring.xml` to output JSON format in non-local profiles
- [ ] Replace any remaining `System.out.println` with SLF4J calls

### TASK-10.11 — Actuator & Micrometer
> **Why:** You can't operate what you can't see. Health and metrics endpoints expose the app's internal state so monitoring tools (and you) can spot trouble early.
> **Done when:** `/actuator/health` returns `UP`, `/actuator/prometheus` lists your custom counters, and those counters increment when you create a post / like / register.
- [ ] Add `spring-boot-starter-actuator` + `micrometer-registry-prometheus` to `pom.xml`
- [ ] Expose `health`, `info`, `metrics`, `prometheus` endpoints
- [ ] Add custom `MeterRegistry` counter for post creations, likes, registrations
- [ ] Secure Actuator endpoints (allow only `ROLE_ADMIN` except `/actuator/health`)

### TASK-10.12 — Distributed tracing
> **Why:** When a request crosses several services, a trace shows exactly where time was spent and which hop failed — far faster than reading each service's logs separately.
> **Done when:** A request shows up as a single trace with timed spans in the Zipkin UI.
- [ ] Add `io.micrometer:micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin` to `pom.xml`
- [ ] Configure `application.yml` → `management.tracing.sampling.probability=1.0` for dev
- [ ] Run Zipkin locally via Docker Compose (`openzipkin/zipkin` image)

---

## Testing

### TASK-10.13 — Backend test coverage gate
> **Why:** A coverage gate stops new untested code from sneaking into the codebase and makes "did we test the error paths?" an automatic check instead of a hope.
> **Done when:** `mvn verify` fails when coverage drops below 80%, and the JaCoCo report shows exception-handler branches as covered.
- [ ] Add `jacoco-maven-plugin` to `pom.xml`, fail build if coverage < 80%
- [ ] Identify and fill test gaps from phases 1–9 (prioritize domain services and controllers)
- [ ] Ensure all exception handler branches are covered

### TASK-10.14 — Frontend component tests
> **Why:** Component tests catch UI regressions — broken validation, a like toggle that stops working — without you manually clicking through the app every time.
> **Done when:** `npm test` runs green and the listed components pass tests covering their error and optimistic-update states, with API calls mocked by MSW.
- [ ] Add `vitest` + `@testing-library/react` + `msw` (mock service worker) to `package.json`
- [ ] Write tests for:
  - `LoginPage` — form validation, submit calls API, error state
  - `PostCard` — renders media, like/save toggles, comment count
  - `LikeButton` — optimistic update
  - `ProtectedRoute` — redirects unauthenticated users
  - `useWebSocket` hook — subscription + message handling

### TASK-10.15 — E2E smoke tests (Playwright)
> **Why:** Every unit test can pass while the wired-together app is broken (bad routing, CORS, env config). E2E tests exercise the real user journeys through a real browser.
> **Done when:** `npx playwright test` drives the full register → login → post → like flow against the running stack and passes.
- [ ] Add `@playwright/test` to `package.json`
- [ ] Create `e2e/` directory with tests:
  - `auth.spec.ts` — register → login → view profile
  - `posts.spec.ts` — create post → view in feed → like → comment
  - `follow.spec.ts` — follow user → see posts in feed → unfollow
  - `messaging.spec.ts` — open DM → send message → verify delivery
- [ ] Add Playwright step to CI (run against the Docker Compose stack)

---

## DevOps

### TASK-10.16 — Dockerfile: backend
> **Why:** A multi-stage build keeps the final image small (JRE only, no Maven or source), which ships faster and exposes less attack surface.
> **Done when:** `docker build` produces an image that boots the API and responds on port 8080.
- [ ] Create `backend/Dockerfile` (multi-stage):
  - Stage 1 (`build`): `maven:3.9-eclipse-temurin-21` — `mvn package -DskipTests`
  - Stage 2 (`run`): `eclipse-temurin:21-jre-alpine` — copy JAR, `EXPOSE 8080`, `ENTRYPOINT`

### TASK-10.17 — Dockerfile: frontend
> **Why:** The React build output is just static files; serving them with nginx is far lighter than running Node in production. SPA fallback makes client-side routes survive a page refresh.
> **Done when:** The container serves the app and refreshing a deep link (e.g. `/search`) returns the app, not a 404.
- [ ] Create `frontend/Dockerfile` (multi-stage):
  - Stage 1 (`build`): `node:20-alpine` — `npm ci && npm run build`
  - Stage 2 (`serve`): `nginx:alpine` — copy `dist/`, configure SPA fallback in `nginx.conf`

### TASK-10.18 — Full docker-compose.yml
> **Why:** One command should bring up the entire stack so any teammate can run an identical environment. Healthchecks ensure dependent services don't start before their dependencies are ready.
> **Done when:** `docker compose up` brings all services to a healthy state and the frontend talks to the backend end-to-end.
- [ ] Update `docker-compose.yml` to include all services:
  - `backend` (depends on `postgres`, `redis`)
  - `frontend` (depends on `backend`)
  - `postgres`, `minio`, `redis`, `zipkin`
- [ ] Add `healthcheck` blocks for all services
- [ ] Document startup order and port mapping in `README.md`

### TASK-10.19 — CI/CD: Docker build & push
> **Why:** Automating image build + push means every merge to main produces a deployable, versioned artifact with zero manual steps — and you can always trace an image back to its commit.
> **Done when:** A push to main produces images in GHCR tagged with both the commit SHA and `latest`.
- [ ] Extend `.github/workflows/ci.yml`:
  - After tests pass: `docker build` backend + frontend images
  - Push to GitHub Container Registry (`ghcr.io`)
  - Tag with `sha:${{ github.sha }}` and `latest` on main branch pushes

---

## 🌱 Beginner Learning Tasks (optional but recommended)

> These are smaller, lower-risk tasks designed to build intuition before (or alongside) the core work above.
> They favour *understanding and measuring* over shipping a big feature. Do them in any order; each names the core task it pairs with.

### TASK-10.20 — Establish a performance baseline with `EXPLAIN ANALYZE`
> _Pairs with TASK-10.1 / TASK-10.2._
> **Why:** Optimising without measuring is guessing. A "before" number is the only way to know whether caching or an index actually helped.
> **Done when:** You have a before/after `EXPLAIN ANALYZE` of the home-feed query saved in `docs/infra/` and can name which index (or seq scan) the planner chose.
- [ ] Run `EXPLAIN ANALYZE` on the home-feed query in `psql` and record the execution time
- [ ] Note whether the planner uses an index scan or a sequential scan
- [ ] Re-run after TASK-10.1/10.2 and compare the numbers

### TASK-10.21 — Tune the HikariCP connection pool
> _Pairs with TASK-10.1._
> **Why:** A pool that's too large overwhelms Postgres with connections; too small and requests queue up waiting. "Bigger" is not "faster".
> **Done when:** `spring.datasource.hikari.maximum-pool-size` is set explicitly in `application.yml` with a one-line comment justifying the value.
- [ ] Read the Hikari docs on pool sizing (start around `CPU cores * 2`)
- [ ] Set `maximum-pool-size` explicitly and add a comment explaining your reasoning
- [ ] Watch the pool metrics under load via `/actuator/metrics/hikaricp.connections.active`

### TASK-10.22 — Add baseline security headers
> _Pairs with TASK-10.9._
> **Why:** A few standard headers block whole classes of attack (MIME sniffing, clickjacking, injected scripts) for almost no effort.
> **Done when:** Responses include `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, and a basic `Content-Security-Policy`, verified in the browser Network tab.
- [ ] Add the three headers in `SecurityConfig` (alongside the HSTS header from TASK-10.9)
- [ ] Confirm each header is present on a page response in DevTools → Network → Headers

### TASK-10.23 — Secrets hygiene check
> _Pairs with TASK-10.5 / TASK-10.18._
> **Why:** A secret committed to git is leaked permanently — it lives in history even after you delete the line.
> **Done when:** No real secret values appear in tracked files, and the app boots reading them from environment variables.
- [ ] Search the repo for hard-coded secrets (DB password, JWT key)
- [ ] Move any found secrets to environment variables
- [ ] Add a `.env.example` with placeholder values and document it in `README.md`

### TASK-10.24 — Trace one request end-to-end
> _Pairs with TASK-10.10._
> **Why:** Builds the mental model that makes structured logging genuinely useful — seeing one request flow through controller → service → persistence.
> **Done when:** You can paste the full, ordered set of log lines for a single "create post" request, all sharing one `requestId`.
- [ ] Trigger one `POST /api/v1/posts` request
- [ ] Grep the logs for its `requestId`
- [ ] Confirm the lines appear in controller → service → adapter order

### TASK-10.25 — Swap one integration test to Testcontainers
> _Pairs with TASK-10.13._
> **Why:** H2 doesn't behave exactly like Postgres (e.g. `ILIKE`, full-text search). A test can pass against H2 but break in production — Testcontainers runs the real database.
> **Done when:** One existing `@DataJpaTest` (e.g. `SearchJpaAdapterIT`) spins up a real Postgres container and passes using genuine Postgres SQL features.
- [ ] Add the Testcontainers + Postgres dependencies (test scope)
- [ ] Convert one IT to use a `@Container PostgreSQLContainer`
- [ ] Confirm the test passes against real Postgres, not H2

### TASK-10.26 — Cover one untested branch
> _Pairs with TASK-10.13._
> **Why:** Practises the core skill behind the coverage gate: reading a report, finding a gap, and writing the targeted test that closes it.
> **Done when:** A previously red (uncovered) exception branch shows as covered in the JaCoCo report after your new test.
- [ ] Open the JaCoCo HTML report and find one uncovered exception branch
- [ ] Write a test that triggers that branch
- [ ] Re-run and confirm the branch is now covered

### TASK-10.27 — Add `.dockerignore` files
> _Pairs with TASK-10.16 / TASK-10.17._
> **Why:** Without it, Docker copies `target/`, `node_modules/`, and `.git/` into the build context — slowing builds and bloating images.
> **Done when:** `docker build` no longer copies those directories and the build context size visibly drops.
- [ ] Add `backend/.dockerignore` (ignore `target/`, `.git/`, `*.md`)
- [ ] Add `frontend/.dockerignore` (ignore `node_modules/`, `dist/`, `.git/`)
- [ ] Re-run `docker build` and confirm the "transferring context" size is smaller

### TASK-10.28 — Write a one-command smoke test script
> _Pairs with TASK-10.18._
> **Why:** A fast "is it alive?" check saves you from clicking around the UI to confirm a deploy didn't break the basics.
> **Done when:** Running the script against the running stack prints a clear pass/fail for each check.
- [ ] Create a `make smoke` target (or `scripts/smoke.sh`)
- [ ] Have it curl `/actuator/health` and one real endpoint (e.g. `GET /api/v1/feed`)
- [ ] Exit non-zero if any check fails

### TASK-10.29 — Write your first ADR (Architecture Decision Record)
> _Pairs with any decision in this phase._
> **Why:** ADRs capture *why* a choice was made, so future-you and teammates don't re-argue settled decisions.
> **Done when:** A short ADR exists at `docs/adr/0001-*.md` with **Context**, **Decision**, and **Consequences** sections.
- [ ] Pick one Phase 10 decision (e.g. "RS256 over HS256" or "Redis for feed cache")
- [ ] Write it up in `docs/adr/0001-<slug>.md` using the Context / Decision / Consequences format
- [ ] Link it from `docs/` so it's discoverable

---

## Advanced Backend (stretch)

> Harder, senior-level backend tasks that build on the core work. Each follows the project's hexagonal layout (`domain/` → `application/` → `adapter/` → `infrastructure/`) and the existing media/post/messaging features. Tackle them once the core phase is solid.

### TASK-10.30 — Chunked / resumable large-file upload (up to 2 GB)
> **Why:** A single multipart POST of a 2 GB video times out, exhausts server memory, and forces the user to restart from zero on any network blip — chunked upload streams it in parts that can resume.
> **Done when:** You can upload a 2 GB file as multiple parts, drop the connection mid-upload, resume, and the reassembled object in MinIO is byte-identical (matching checksum).
- [ ] Extend the storage out-port + `MinioStorageAdapter` to use **multipart upload** (`createMultipartUpload`, presigned `uploadPart` URLs, `completeMultipartUpload`, `abortMultipartUpload`)
- [ ] Add `MediaController` endpoints: `POST /api/v1/media/uploads` (initiate → `uploadId`), `GET .../uploads/{uploadId}/parts` (which parts exist, for resume), `POST .../uploads/{uploadId}/complete`
- [ ] Flyway migration for an `upload_session` table (uploadId, key, parts, status, created_at)
- [ ] Enforce a 2 GB total cap + a per-part size (e.g. 5–10 MB) and validate part numbers
- [ ] Abort/cleanup path for stale or cancelled sessions

### TASK-10.31 — Spring Batch: bulk import posts for a user
> **Why:** Importing thousands of posts (e.g. migrating a user from another platform) in one request times out and can't recover from a mid-way failure — Spring Batch chunks the work with restart and skip handling.
> **Done when:** A job reads a CSV/JSON of N posts, writes them in chunks, and after a forced mid-job failure restarts from the last committed chunk (not from zero), visible in `BATCH_JOB_EXECUTION`.
- [ ] Add `spring-boot-starter-batch` + a Flyway migration for the Spring Batch metadata tables
- [ ] Define a chunk-oriented `Step`: `ItemReader` (CSV/JSON) → `ItemProcessor` (validate + map to the `Post` domain model) → `ItemWriter` (persist via `PostRepository`)
- [ ] Configure chunk size, a skip policy for bad rows, and retry on transient errors
- [ ] Trigger via `POST /api/v1/admin/imports/posts` (returns a job execution id) + a status endpoint
- [ ] Verify restartability: kill the job mid-run, relaunch, confirm it resumes from the last committed chunk

### TASK-10.32 — Async processing with tuned executors + virtual threads
> **Why:** Long side-tasks (thumbnailing, transcode kickoff, import triggers) shouldn't block the request thread, and Java 21 virtual threads let you run many blocking-I/O tasks cheaply.
> **Done when:** A flagged endpoint returns `202 Accepted` immediately while the work runs on a background executor, and toggling virtual threads is verifiable in a thread dump (`VirtualThread` names).
- [ ] Define a named, bounded `ThreadPoolTaskExecutor` bean instead of relying on the default
- [ ] Annotate the heavy side-task method with `@Async("…")` returning `CompletableFuture<>`
- [ ] Enable virtual threads (`spring.threads.virtual.enabled=true`) and compare behaviour under load
- [ ] Make one endpoint return `202` with a status URL while the work runs async
- [ ] Confirm via thread dump / metric that the work runs off the request thread

### TASK-10.33 — Idempotency keys for unsafe POSTs
> **Why:** Flaky networks make clients retry POSTs; without idempotency a retry creates duplicate posts or messages, and a retried import could double-write.
> **Done when:** Sending the same `Idempotency-Key` twice to `POST /api/v1/posts` creates exactly one post and the second call returns the original response (verify a single DB row).
- [ ] Accept an `Idempotency-Key` header on create-post and send-message endpoints
- [ ] Flyway migration for an `idempotency_key` table (key, request hash, stored response, status, created_at)
- [ ] Add an interceptor/guard that records the key in the same transaction and short-circuits duplicates
- [ ] Return the stored response on a repeated key; `409 Conflict` if the same key arrives with a different payload
- [ ] Add a test firing the same key twice and asserting one side-effect

### TASK-10.34 — Streaming large exports (CSV/ZIP, no OOM)
> **Why:** Building a full data export in memory blows up the heap on large accounts — streaming writes bytes to the response as they're produced, keeping memory flat.
> **Done when:** `GET /api/v1/users/me/export` streams a CSV/ZIP of all the user's posts while heap usage stays steady (watch `/actuator/metrics/jvm.memory.used` during a large download).
- [ ] Add an endpoint returning `StreamingResponseBody` with `Content-Disposition: attachment`
- [ ] Stream rows from a cursor-based / `Stream<>` repository query — never collect the full result into a list
- [ ] Use `@Transactional(readOnly = true)` + a JDBC fetch size so Hibernate streams instead of buffering
- [ ] Build the ZIP/CSV incrementally, flushing per chunk
- [ ] Verify against a large dataset that heap stays flat throughout the download

### TASK-10.35 — Scheduled jobs with distributed locking (ShedLock)
> **Why:** `@Scheduled` jobs fire on every instance, so 3 replicas run a cleanup job 3× at once — a distributed lock guarantees exactly one instance runs each tick.
> **Done when:** With two app instances running, a scheduled job executes on only one instance per tick (verify via logs and the lock row).
- [ ] Add `shedlock-spring` + a JDBC lock provider and a Flyway migration for the `shedlock` table
- [ ] Create `@Scheduled` jobs: purge orphaned media, expire stale multipart-upload sessions (TASK-10.30), delete old idempotency keys (TASK-10.33)
- [ ] Wrap each job with `@SchedulerLock(name, lockAtMostFor)`
- [ ] Run two instances locally and confirm the job runs once per tick, not twice
- [ ] Emit a log line / metric per job run for auditability
