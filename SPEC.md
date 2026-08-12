# Spec: k6 Load & Soak Testing (TASK-10.40)

> Scope: this spec covers only TASK-10.40 (the `k6/` load/soak test suite), not the whole application. It supersedes the example code in `docs/tasks/phase-10/TASK-10.40-k6-load-soak-testing.md` where that file's sample scripts don't match this codebase's real API contracts (see "Corrections" below).

## Objective

Build a k6 load-test suite under `k6/` covering the three hottest API journeys — login, home-feed scroll, create-post — plus a soak scenario, with pass/fail thresholds tied to SLO targets. Run it against the local Docker Compose stack and record real baseline numbers.

- **Who's it for:** backend/platform engineers who need reproducible before/after numbers around performance work (pairs with [TASK-10.2](docs/tasks/phase-10/TASK-10.2-hikaricp-pool-tuning.md) HikariCP tuning and [TASK-10.3](docs/tasks/phase-10/TASK-10.3-redis-caching.md) Redis caching).
- **Success looks like:**
  - `k6 run` on each scenario exits `0` with thresholds passing under the Compose stack's normal capacity.
  - Deliberately tightening a threshold reproduces `✗ N thresholds failed` and exit code `99` (proves the gate actually works).
  - The soak scenario runs ≥8 minutes of steady load without HikariCP pool exhaustion (`hikaricp.connections.active` pinned at max) or unbounded `jvm.memory.used` growth — or, if it does find one, that's documented as a real finding.
  - `docs/infra/load-test.md` contains real recorded numbers, not the task file's placeholder table.

## Corrections vs. the original task file

The task file's sample scripts were written against a generic/assumed API shape that doesn't match this codebase. Confirmed by reading the actual DTOs and seed migration:

| Task file assumes | Actual codebase | Source |
|---|---|---|
| `POST /auth/login` body `{ username, password }` | `LoginRequest` is `{ identifier, password }` | `LoginRequest.java` |
| A `seed_user` account exists from V2 migration | V2 only seeds `demo_user`, **no password** ("no auth yet") | `V2__seed_data.sql` |
| `res.data.accessToken` | Correct as written — `AuthResponse` is wrapped in `ApiResponse<T>{ data, error, timestamp }` | `ApiResponse.java`, `AuthResponse.java` |
| `POST /posts` body `{ caption, location, mediaUrls, mediaType }` | `CreatePostRequest` requires `mediaItems: [{ mediaKey, mediaType, width, height, duration, fileSizeBytes, sortOrder }]`; `mediaKey` only comes from a real presigned upload | `CreatePostRequest.java`, `MediaController.java` |

Resolved decisions (confirmed with you):
1. **Test account:** no working seed credentials exist, so `setup()` registers a dedicated `loadtest_user` via `POST /api/v1/auth/register` (tolerating a `409` if it already exists from a prior run), then logs in with `identifier: 'loadtest_user'`.
2. **Media for create-post:** `setup()` does one presigned-upload (`POST /media/upload-url` → `PUT` a small fixture image directly to MinIO) and every iteration's `POST /posts` reuses that same `mediaKey`. Full per-iteration upload was rejected as unnecessary MinIO load for what's meant to be a posts-endpoint test.
3. **SLO thresholds:** TASK-10.31 (SLI/SLO doc) isn't done yet — use the task file's own defaults: Login p95≤500ms, Feed p95≤800ms, Create-post p95≤1000ms, error rate≤1%. Revisit once `docs/infra/slo.md` exists.

## Tech Stack

- **k6** (`winget install k6`) — JavaScript ES6-module runtime, not Node.js (no `require`, `fs`, `path`; only `k6/http`, `k6/metrics`, `k6`, and local files via `open()`).
- Target: backend at `http://localhost:8080` (Spring Boot 3.3.4), stack started via the existing root `docker-compose.yml` (Postgres, MinIO, backend, frontend).

## Commands

```powershell
# Install / verify
winget install k6
k6 version

# Start the stack (repo root)
docker compose up -d

# Run one scenario
k6 run k6/scenarios/login.js
k6 run k6/scenarios/home-feed.js
k6 run k6/scenarios/create-post.js
k6 run k6/scenarios/soak.js

# Run everything combined
k6 run k6/load-test.js

# Point at a different environment
k6 run --env BASE_URL=http://staging.example.com k6/scenarios/login.js
```

## Project Structure

```
k6/
├── fixtures/
│   └── test-image.jpg      # small binary fixture, read via open(path, 'b'); reused across create-post iterations
├── lib/
│   ├── auth.js              # register-if-needed + login helper, returns accessToken
│   └── media.js             # one-time presigned-upload helper used by create-post's setup()
├── scenarios/
│   ├── login.js
│   ├── home-feed.js
│   ├── create-post.js
│   └── soak.js
└── load-test.js             # combined multi-scenario entry point
docs/infra/load-test.md      # baseline numbers + run instructions (new)
```

## Code Style

- One `export const options = { stages, thresholds }` per scenario; `stages` is always ramp-up → hold → ramp-down.
- Tag every `http.*` call with `tags: { endpoint: '<name>' }` so thresholds can target `http_req_duration{endpoint:<name>}` — without tags, every scenario's requests get aggregated together and you can't tell which endpoint breached its SLO.
- `setup()` does anything shared exactly once (register+login, upload the fixture image); `default(data)` never re-authenticates or re-uploads per iteration.
- `sleep(1-3)` after every user-facing action to model think time.
- `const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'` at the top of every file that makes HTTP calls.

Corrected login helper (`k6/lib/auth.js`):

```javascript
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export function registerAndLogin(username, email, password) {
  http.post(
    `${BASE_URL}/api/v1/auth/register`,
    JSON.stringify({ username, email, password, fullName: 'Load Tester' }),
    { headers: { 'Content-Type': 'application/json' } }
  ); // 201 on first run, 409 on subsequent runs — both fine, result unused

  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ identifier: username, password }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(res, {
    'login status is 200': (r) => r.status === 200,
    'login returns access token': (r) => {
      try { return JSON.parse(r.body).data.accessToken !== undefined; }
      catch { return false; }
    },
  });

  return JSON.parse(res.body).data.accessToken;
}
```

## Testing Strategy

- This suite *is* the test — there's no "test the tests" layer. Verification means running each scenario against the live Compose stack and reading k6's own summary output.
- k6's built-in threshold mechanism already provides the pass/fail gate (exit code `99` on breach) — no custom scripting needed for that.
- **Not** wired into `backend-ci`/`frontend-ci` — these take minutes and generate real load, matching the task file's own guidance. A scheduled/separate pipeline job is a future task, out of scope here.
- Manual verification checklist:
  1. `docker compose up -d`, wait for backend healthy.
  2. Run each of the 4 scenarios individually — confirm `✓ N thresholds passed`, exit code `0`.
  3. Temporarily tighten one threshold (e.g. `p(95)<1`), rerun, confirm `✗ 1 thresholds failed` + exit code `99`, then revert.
  4. Run `soak.js` (≥8 min), watch `hikaricp.connections.active` and `jvm.memory.used` via Actuator in a second terminal.
  5. Record the real numbers into `docs/infra/load-test.md`.

## Boundaries

- **Always:** run only against local Docker Compose or an explicitly designated staging URL — never production; use the dedicated `loadtest_user` account, never `demo_user` or a real user account; keep `sleep()` think-time in every scenario.
- **Ask first:** wiring k6 into any CI pipeline; adjusting HikariCP pool size or other production config in response to soak-test findings (that's TASK-10.2's scope, not this task's); adding new dependencies.
- **Never:** commit real credentials (use `__ENV` for anything beyond the disposable load-test account); write to `docs/infra/load-test.md` in a way that discards prior baseline rows — append, per the task file's own convention.

## Success Criteria

- [ ] `k6/lib/auth.js`, `k6/lib/media.js`, `k6/fixtures/test-image.jpg`, 4 scenario files, and `k6/load-test.js` exist.
- [ ] Each scenario's `thresholds` matches the agreed defaults (login p95<500ms, feed p95<800ms, create-post p95<1000ms, error rate<1%), each tagged by endpoint.
- [ ] All 4 scenarios pass their thresholds when run against the local Compose stack.
- [ ] Deliberately breaking a threshold reproduces exit code `99`.
- [ ] Soak scenario completes ≥8 min steady load; Actuator metrics observed and the result (clean or a real finding) is noted.
- [ ] `docs/infra/load-test.md` has real recorded numbers.

## Open Questions

None outstanding — the three ambiguities in the original task file (test account, post media, SLO source) were resolved above.
