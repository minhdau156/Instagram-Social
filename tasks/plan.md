# Implementation Plan: TASK-10.40 — k6 Load & Soak Testing

## Overview

Build a `k6/` directory with load-test scripts for the three hottest API journeys (login, home-feed, create-post) plus a soak test, thresholds tied to SLO defaults, and a documented baseline in `docs/infra/load-test.md`. Full context, corrections vs. the original task doc, and resolved decisions live in `SPEC.md` at the repo root — read that first.

## Architecture Decisions

- **Test account via `setup()` registration, not seed data** — no working seed user exists (`V2__seed_data.sql` only seeds a passwordless `demo_user`), so every scenario's `setup()` registers a dedicated `loadtest_user` (tolerating `409` on reruns) rather than relying on fixture DB rows.
- **Media uploaded once, reused across iterations** — `create-post.js`'s `setup()` does a single real presigned-upload to MinIO and every iteration's `POST /posts` reuses that one `mediaKey`, instead of uploading a fresh file per iteration. Keeps load focused on the posts endpoint rather than MinIO throughput.
- **SLO thresholds from the task doc's defaults** — TASK-10.31 (formal SLO doc) isn't done yet, so thresholds are hardcoded to the task doc's fallback numbers (login p95<500ms, feed p95<800ms, create-post p95<1000ms, error rate<1%) and flagged for revisit later.
- **`docker-compose.yml` only covers infra** — Postgres/MinIO/Redis/observability run via compose; backend and frontend are started separately via `mvn spring-boot:run` / `npm run dev`, per `AGENTS.md`. Documented explicitly in `docs/infra/load-test.md` so it isn't assumed otherwise.

## Task List

### Phase 1: Foundation
- [ ] Task 1: Fixture image + auth helper (`k6/fixtures/test-image.jpg`, `k6/lib/auth.js`)

### Phase 2: Core scenarios (the three hottest journeys)
- [ ] Task 2: Login scenario (`k6/scenarios/login.js`)
- [ ] Task 3: Home-feed scenario (`k6/scenarios/home-feed.js`)
- [ ] Task 4: Media upload helper (`k6/lib/media.js`)
- [ ] Task 5: Create-post scenario (`k6/scenarios/create-post.js`)

### Checkpoint A — after Tasks 1-5
- [ ] All three journey scenarios individually pass thresholds against the locally running stack
- [ ] Satisfies the task doc's core checklist item ("Add a k6/ directory with scripts for the hottest journeys")
- [ ] Review with human before continuing

### Phase 3: Soak + combined runner + documentation
- [ ] Task 6: Soak scenario (`k6/scenarios/soak.js`)
- [ ] Task 7: Combined entry point (`k6/load-test.js`)

### Checkpoint B — after Tasks 6-7
- [ ] Soak test completed a full ≥8min run with metrics observed
- [ ] Combined runner works
- [ ] Review with human before final threshold-failure proof + documentation

- [ ] Task 8: Threshold-failure proof + baseline documentation (`docs/infra/load-test.md`)

### Final Checkpoint
- [ ] All SPEC.md success criteria met
- [ ] `docs/infra/load-test.md` committed with real baseline
- [ ] Ready for human review / commit

## Task Detail

### Task 1 — Fixture image + auth helper
**Description:** Add a small real binary JPEG fixture and the shared k6 auth helper: `registerOnce()` (POSTs `/api/v1/auth/register`, tolerates `201`/`409`) and `login()` (POSTs `{identifier, password}` to `/api/v1/auth/login`, returns `data.accessToken`). Corrects the task doc's `username` field to the real `identifier` field.
**Acceptance criteria:**
- [ ] `registerOnce()` tolerates both first-run (`201`) and rerun (`409`)
- [ ] `login()` returns `JSON.parse(res.body).data.accessToken`
**Verification:** exercised by Task 2's first run (no independent smoke test needed for a helper with no `options`).
**Dependencies:** None
**Files:** `k6/fixtures/test-image.jpg`, `k6/lib/auth.js`
**Estimated scope:** XS (2 files)

### Task 2 — Login scenario
**Description:** `k6/scenarios/login.js`. `setup()` calls `registerOnce()` once. `default()` calls `POST /auth/login` directly every iteration (tagged `scenario:login`) — measuring the login endpoint itself, not going through the shared helper's registration step per iteration. Stages: 30s→20 VUs, 1m→50 VUs, 30s→0. Thresholds: `http_req_duration{scenario:login}` p95<500ms, `http_req_failed` rate<1%.
**Acceptance criteria:**
- [ ] Uses `identifier`, not `username`
- [ ] Thresholds match SPEC.md defaults, tagged `{scenario:login}`
**Verification:** `k6 run k6/scenarios/login.js` against the locally running backend → `✓ N thresholds passed`, exit code `0`.
**Dependencies:** Task 1
**Files:** `k6/scenarios/login.js`
**Estimated scope:** S (1 file)

### Task 3 — Home-feed scenario
**Description:** `k6/scenarios/home-feed.js`. `setup()` uses `auth.js` to register+login once. `default(data)` fetches `GET /api/v1/feed/home?page=0&size=10` then `page=1`, with `sleep()` between, tagged `endpoint:feed`. Stages: 30s→30, 2m→100, 30s→0. Thresholds: p95<800ms, p99<1500ms.
**Acceptance criteria:**
- [ ] Reuses one token from `setup()` across all VU iterations — no per-iteration login
**Verification:** `k6 run k6/scenarios/home-feed.js` → thresholds pass, exit `0`.
**Dependencies:** Task 1
**Files:** `k6/scenarios/home-feed.js`
**Estimated scope:** S (1 file)

### Task 4 — Media upload helper
**Description:** `k6/lib/media.js`. One-time presigned-upload flow: `POST /api/v1/media/upload-url` with `{filename: 'test-image.jpg', contentType: 'image/jpeg'}` (bearer token required), then `http.put(presignedUrl, open('../fixtures/test-image.jpg', 'b'))` to PUT the fixture straight to MinIO. Returns the resulting `mediaKey`.
**Acceptance criteria:**
- [ ] Exported function takes an access token, returns a `mediaKey` string
- [ ] `check()`s both the upload-url call and the MinIO PUT
**Verification:** exercised end-to-end by Task 5.
**Dependencies:** Task 1
**Files:** `k6/lib/media.js`
**Estimated scope:** XS (1 file)

### Task 5 — Create-post scenario
**Description:** `k6/scenarios/create-post.js`. `setup()` logs in (via `auth.js`) then uploads once (via `media.js`) to get one reusable `mediaKey`. `default(data)` POSTs `/api/v1/posts` with the real `mediaItems` shape, tagged `endpoint:create-post`. Stages: 30s→10, 1m→30, 30s→0. Threshold: p95<1000ms.
**Acceptance criteria:**
- [ ] Before hardcoding `mediaItems` fields, read `MediaItemRequest.java`'s validation annotations to confirm which of `width`/`height`/`duration`/`fileSizeBytes`/`sortOrder` are required vs. optional, and use the fixture's real dimensions/byte size for required ones
- [ ] Every iteration creates a real post (`201`), reusing the same `mediaKey` from `setup()`
- [ ] Caption includes a per-iteration unique marker (timestamp/VU/iter)
**Verification:** `k6 run k6/scenarios/create-post.js` → thresholds pass, exit `0`. Spot-check `GET /api/v1/users/me/posts` (or DB) to confirm posts were actually created, not silently failing validation.
**Dependencies:** Task 1, Task 4
**Files:** `k6/scenarios/create-post.js`
**Estimated scope:** S (1 file)

### Task 6 — Soak scenario
**Description:** `k6/scenarios/soak.js`. `setup()` logs in once. `default(data)` hits `GET /feed/home` steadily for ≥8 minutes (stages: 1m ramp / 8m hold / 1m down @ 30 VUs). Looser thresholds: p95<1000ms, `checks: rate>0.99`.
**Acceptance criteria:**
- [ ] Run duration ≥8 min steady at the specified VU count
**Verification:** `k6 run k6/scenarios/soak.js` (~10 min). In parallel, poll `curl http://localhost:8080/actuator/metrics/hikaricp.connections.active` and `.../jvm.memory.used` a few times during the run (actuator is public, confirmed — no auth token needed). Record whether values plateau or climb unbounded.
**Dependencies:** Task 1
**Files:** `k6/scenarios/soak.js`
**Estimated scope:** S (1 file)

### Task 7 — Combined entry point
**Description:** `k6/load-test.js`. Combines `login` and `feed` as parallel named scenarios via k6's `scenarios` executor config, reusing exec functions from Tasks 2 and 3, with its own top-level `thresholds`.
**Acceptance criteria:**
- [ ] Runs both scenarios per configured `startTime` offsets without import errors
**Verification:** `k6 run k6/load-test.js` completes, exit `0`, both scenario tags appear in the summary.
**Dependencies:** Task 2, Task 3
**Files:** `k6/load-test.js`
**Estimated scope:** S (1 file)

### Task 8 — Threshold-failure proof + baseline documentation
**Description:** Temporarily tighten one scenario's threshold (e.g. `login.js` → `p(95)<1`), rerun, confirm `✗ 1 thresholds failed` + exit code `99`, then revert. Create `docs/infra/load-test.md` using the task doc's table structure, filled in with real p50/p95/p99/error-rate numbers from Tasks 2, 3, 5, 6's actual runs — not placeholders. Explicitly document that backend/frontend are started separately from `docker compose up -d`.
**Acceptance criteria:**
- [ ] Exit code 99 reproduced, then the threshold edit is reverted (`git diff` clean on scenario files)
- [ ] `docs/infra/load-test.md` has real numbers, k6 version, date, and the SLO table from SPEC.md
**Verification:** `git diff` shows no unintended change after revert; numbers in `load-test.md` are traceable to actual terminal output from this session.
**Dependencies:** Checkpoint A, Task 6
**Files:** `docs/infra/load-test.md`
**Estimated scope:** S (1 file + a revert)

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| `docker-compose.yml` doesn't run backend/frontend — easy to assume `docker compose up -d` alone is enough and hit connection-refused | Med | Documented explicitly in `docs/infra/load-test.md` prerequisites |
| `mediaItems` validation may require fields the fixture doesn't naturally have if values are faked | Low | Task 5 reads `MediaItemRequest.java` validation before hardcoding values |
| Soak test genuinely finds a HikariCP/memory issue | Info, not blocking | Documented as a real finding, not fixed here (belongs to TASK-10.2) |
| Local-machine numbers aren't portable to CI/staging | Low | `load-test.md` notes it's a local-dev baseline, per the task doc's own convention |

## Open Questions

None — all decisions resolved in `SPEC.md` or during planning. One implementation-time detail intentionally deferred to Task 5: exact optional `mediaItems` fields, to be confirmed against `MediaItemRequest.java` rather than guessed now.
