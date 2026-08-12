# k6 Load Test Report

**Date:** 2026-08-12
**Target:** `http://localhost:8080` (local backend, `mvn spring-boot:run`, profile `local`)
**Tool:** k6 v2.1.0 (windows/amd64)

## Summary

| Scenario | VUs (stages) | Duration | Requests | Failure rate | Checks passed | Thresholds met |
|---|---|---|---|---|---|---|
| `login.js` | 0→2→3→0 | 60s | 61 | 1.63% | 100% (120/120) | No (latency + error-rate thresholds) |
| `feed.js` | 0→2→3→0 | 60s | 48 | 2.08% | 100% (48/48) | No (error-rate threshold) |

Both runs left the backend healthy afterward (`/actuator/health` → `200`).

## Incident before the results below: backend crash under the original config

The scenario scripts originally ramped to 10 VUs (`{30s→5}, {1m→10}, {30s→0}`). Running `login.js` at that
load crashed the backend mid-test:

```
[ERROR] Failed to execute goal org.springframework.boot:spring-boot-maven-plugin:3.3.4:run
        Process terminated with exit code: -1073741819 (0xC0000005 access violation)
```

No `hs_err_pid*.log` was produced, so this wasn't a clean JVM crash report — the process was hard-killed.
The resulting run showed 92% request failures, which was really "backend unreachable," not a real
latency/error measurement, so that run is not reported here.

The backend was restarted, VUs were reduced to `0→2→3→0` in both `k6/scenarios/login.js` and
`k6/scenarios/feed.js`, and the tests below were re-run cleanly with the backend surviving the full run.
**This machine (local dev box, all infra containers + IDE + JVM running concurrently) could not sustain
10 concurrent VUs against a `mvn spring-boot:run` backend** — treat this as an environment/resourcing
limit for local testing, not a validated finding about the backend's real capacity. Re-verify against a
dedicated/staging environment before drawing capacity conclusions.

## Bug found and fixed: `feed.js` targeted a stale endpoint

`feed.js` called `GET /api/v1/feed/home?page=0&size=10`, which doesn't exist — the real endpoint
(`FeedController`) is `GET /api/v1/feed?cursor=&limit=20` (cursor-based pagination, not page/size). The
first corrected-VU run of `feed.js` (before this fix) failed 98% of requests with:

```
org.springframework.web.servlet.resource.NoResourceFoundException: No static resource api/v1/feed/home.
```

`k6/scenarios/feed.js` was updated to call `GET /api/v1/feed?limit=10`. The results below are from the
run after this fix.

## Test 1 — `login.js`

**Scenario:** register-or-reuse a `loadtester` user in `setup()`, then repeatedly `POST /api/v1/auth/login`.

**Stages:** ramp 0→2 VUs (15s), hold 3 VUs (30s), ramp down to 0 (15s).

**Thresholds:**
- `http_req_duration{scenario:login}`: `p(95)<500ms` → **failed**, actual `p(95)=1.22s`
- `http_req_failed`: `rate<0.01` → **failed**, actual `rate=1.63%` (1/61 requests)

**Results:**

| Metric | Value |
|---|---|
| Checks | 120/120 passed (100%) — `login is 200`, `token returned` |
| HTTP requests | 61 |
| Iterations | 59 |
| Failed requests | 1 (1.63%) |
| `http_req_duration` avg / med | 882.19ms / 782.39ms |
| `http_req_duration` p90 / p95 / max | 1.21s / 1.43s / 3.7s |
| `iteration_duration` avg / p95 | 1.82s / 2.23s |
| Data sent / received | 12 kB / 100 kB |

## Test 2 — `feed.js`

**Scenario:** register-or-reuse a `loadtester` user in `setup()`, then repeatedly `GET /api/v1/feed?limit=10`
with the bearer token.

**Stages:** ramp 0→2 VUs (15s), hold 3 VUs (30s), ramp down to 0 (15s).

**Thresholds:**
- `http_req_duration{scenario:login}`: `p(95)<700ms` → passed trivially (no requests carry the `login`
  scenario tag in this script — `p(95)=0s`)
- `http_req_failed`: `rate<0.01` → **failed**, actual `rate=2.08%` (1/48 requests)

**Results:**

| Metric | Value |
|---|---|
| Checks | 48/48 passed (100%) — `login is 200`, `token returned`, `feed page 0 is 200` |
| HTTP requests | 48 |
| Iterations | 46 |
| Failed requests | 1 (2.08%) |
| `http_req_duration` avg / med | 390.24ms / 157.51ms |
| `http_req_duration` p90 / p95 / max | 478.53ms / 764.96ms / 8.51s |
| `iteration_duration` avg / p95 | 2.38s / 2.69s |
| Data sent / received | 28 kB / 33 kB |

## Follow-ups

1. **Backend crash at higher concurrency (10 VUs)** — reproduce on a non-dev-box environment to determine
   whether this is purely local resource contention or an actual stability issue worth a bug ticket.
2. **Latency thresholds not met** even at 3 VUs (`login` p95 1.22s vs. 500ms target, feed p95 ~765ms).
   Likely inflated by running the JVM cold on a loaded dev machine — re-run after a warm-up period and on
   a quieter machine before treating these as real regressions.
3. **~1-2% error rate** in both scenarios at only 3 VUs — worth checking backend logs for the specific
   failing request (rate limiter? transient connection reset?) rather than assuming it's noise.
4. Scenario VUs in `login.js` and `feed.js` were reduced from the original 5→10 ramp to 2→3 to keep the
   local backend alive during this run. Restore the original ramp (or scale further) once tested against
   a proper load-test environment.
