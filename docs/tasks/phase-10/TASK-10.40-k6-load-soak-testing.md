# TASK-10.40 — Load & soak testing (k6)

## Overview

Create a `k6/` directory with load test scripts for the three hottest API journeys (login, home-feed scroll, create post). Define ramping virtual-user stages and threshold assertions tied to the SLOs from [TASK-10.31](TASK-10.31-sli-slo-error-budget.md). Add a soak scenario that runs steady load for several minutes to expose memory leaks and connection-pool exhaustion. Document baseline numbers in `docs/infra/load-test.md` and configure the threshold check to make the k6 run exit with a non-zero code when an SLO is breached.

---

## Level

Core · Pairs with [TASK-10.31](TASK-10.31-sli-slo-error-budget.md) (SLOs), [TASK-10.2](TASK-10.2-hikaricp-pool-tuning.md) (HikariCP tuning), and [TASK-10.3](TASK-10.3-redis-caching.md) (Redis caching)

---

## Why

Functional tests prove correctness at one request at a time. They say nothing about what happens at 200 concurrent users posting to the feed endpoint simultaneously, or whether the JVM heap grows unbounded after 10 minutes of steady traffic. Load testing finds the breaking point — the RPS at which p95 latency crosses the SLO threshold — before real users do. Soak testing reveals slower failures like connection-pool exhaustion (`HikariCP — Connection is not available, request timed out after 30000ms`) and memory leaks that only appear after sustained load. Running k6 against the Docker Compose stack gives you reproducible baseline numbers you can compare against after each performance-related change.

---

## Prerequisites

- **k6 must be installed.** Install from [k6.io/docs/getting-started/installation](https://k6.io/docs/getting-started/installation/):
  ```powershell
  winget install k6
  ```
  Verify: `k6 version`
- The full Docker Compose stack must be running (backend at `http://localhost:8080`, frontend at `http://localhost:5173`). See [TASK-10.46](TASK-10.46-docker-compose.md) for the Compose file, or start services manually.
- A test user must exist in the database. The V2 seed migration creates a `seed_user` account; use its credentials, or register a new account via the API before running load tests.
- [TASK-10.31](TASK-10.31-sli-slo-error-budget.md) — review the SLO targets before writing the threshold values. If TASK-10.31 is not yet complete, use these defaults:
  - Login p95 ≤ 500 ms
  - Feed p95 ≤ 800 ms
  - Create post p95 ≤ 1000 ms
  - Error rate ≤ 1%
- Concepts to skim:
  - **k6 Virtual Users (VUs)** — each VU runs the test function in a loop. 100 VUs each making 1 req/s = 100 RPS.
  - **Stages** — a `stages` array ramps VUs up, holds them steady, then ramps them down.
  - **Thresholds** — assertions on metrics (e.g., `http_req_duration` p95) that make the run exit with code 99 on failure.
  - **Soak test** — a long-duration test at moderate load to find slow degradation, not a peak test.
  - **`k6 run`** — executes a JavaScript test script.

---

## Files to Create / Modify

```
k6/scenarios/login.js             (new)
k6/scenarios/home-feed.js         (new)
k6/scenarios/create-post.js       (new)
k6/scenarios/soak.js              (new)
k6/lib/auth.js                    (new — shared login helper)
k6/load-test.js                   (new — combines all scenarios)
docs/infra/load-test.md           (new — baseline numbers and instructions)
```

---

## Step-by-Step

### 1. Install k6

```powershell
winget install k6
k6 version
```

Expected: `k6 v0.5x.x (...)` (any recent version works).

---

### 2. Create the directory structure

```powershell
New-Item -ItemType Directory -Path "C:\workspace\Instagram-Social\k6\scenarios" -Force
New-Item -ItemType Directory -Path "C:\workspace\Instagram-Social\k6\lib" -Force
New-Item -ItemType Directory -Path "C:\workspace\Instagram-Social\docs\infra" -Force
```

---

### 3. Create the shared auth helper

Create `k6/lib/auth.js`:

```javascript
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

/**
 * Logs in and returns a JWT access token.
 * Call this once in setup() and share the token via exported data.
 */
export function login(username, password) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username, password }),
    { headers: { 'Content-Type': 'application/json' } }
  );

  check(res, {
    'login status is 200': (r) => r.status === 200,
    'login returns access token': (r) => {
      try {
        return JSON.parse(r.body).data.accessToken !== undefined;
      } catch {
        return false;
      }
    },
  });

  return JSON.parse(res.body).data.accessToken;
}

export const BASE_URL_EXPORT = BASE_URL;
```

---

### 4. Create the login scenario

Create `k6/scenarios/login.js`:

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '30s', target: 20 },   // ramp up to 20 VUs
    { duration: '1m',  target: 50 },   // hold at 50 VUs
    { duration: '30s', target: 0  },   // ramp down
  ],
  thresholds: {
    // p95 of all HTTP request durations must be below 500ms
    'http_req_duration{scenario:login}': ['p(95)<500'],
    // Less than 1% of requests should fail
    'http_req_failed': ['rate<0.01'],
  },
};

export default function () {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ username: 'seed_user', password: 'changeme' }),
    { headers: { 'Content-Type': 'application/json' },
      tags: { scenario: 'login' } }
  );

  check(res, {
    'login is 200': (r) => r.status === 200,
    'token returned': (r) => {
      try { return !!JSON.parse(r.body).data.accessToken; } catch { return false; }
    },
  });

  sleep(1); // simulate realistic user think time
}
```

---

### 5. Create the home-feed scenario

Create `k6/scenarios/home-feed.js`:

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { login } from '../lib/auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '30s', target: 30 },
    { duration: '2m',  target: 100 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    'http_req_duration{endpoint:feed}': ['p(95)<800', 'p(99)<1500'],
    'http_req_failed': ['rate<0.01'],
  },
};

// setup() runs once before all VUs start — use it to get a shared auth token
export function setup() {
  return { token: login('seed_user', 'changeme') };
}

export default function (data) {
  const params = {
    headers: { Authorization: `Bearer ${data.token}` },
    tags: { endpoint: 'feed' },
  };

  // Page 0 — simulating the user opening the app
  let res = http.get(`${BASE_URL}/api/v1/feed/home?page=0&size=10`, params);
  check(res, { 'feed page 0 is 200': (r) => r.status === 200 });

  sleep(2);

  // Page 1 — simulating the user scrolling
  res = http.get(`${BASE_URL}/api/v1/feed/home?page=1&size=10`, params);
  check(res, { 'feed page 1 is 200': (r) => r.status === 200 });

  sleep(1);
}
```

---

### 6. Create the create-post scenario

Create `k6/scenarios/create-post.js`:

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { login } from '../lib/auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m',  target: 30 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    'http_req_duration{endpoint:create-post}': ['p(95)<1000'],
    'http_req_failed': ['rate<0.01'],
  },
};

export function setup() {
  return { token: login('seed_user', 'changeme') };
}

export default function (data) {
  const params = {
    headers: {
      Authorization: `Bearer ${data.token}`,
      'Content-Type': 'application/json',
    },
    tags: { endpoint: 'create-post' },
  };

  const body = JSON.stringify({
    caption: `Load test post ${Date.now()}`,
    location: 'Testland',
    mediaUrls: ['https://example.com/test-image.jpg'],
    mediaType: 'IMAGE',
  });

  const res = http.post(`${BASE_URL}/api/v1/posts`, body, params);
  check(res, { 'post created is 201': (r) => r.status === 201 });

  sleep(3); // post creation is less frequent than feed reads
}
```

---

### 7. Create the soak scenario

Create `k6/scenarios/soak.js`:

```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';
import { login } from '../lib/auth.js';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';

export const options = {
  // Soak: steady load for 10 minutes to surface memory leaks / pool exhaustion
  stages: [
    { duration: '1m',  target: 30 },   // ramp up
    { duration: '8m',  target: 30 },   // hold steady
    { duration: '1m',  target: 0  },   // ramp down
  ],
  thresholds: {
    // Soak thresholds are slightly looser — we care about degradation over time
    'http_req_duration': ['p(95)<1000'],
    'http_req_failed': ['rate<0.01'],
    // Fail if error rate climbs above 2% at any point (regression indicator)
    'checks': ['rate>0.99'],
  },
};

export function setup() {
  return { token: login('seed_user', 'changeme') };
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.token}` };

  const feedRes = http.get(`${BASE_URL}/api/v1/feed/home?page=0&size=10`, { headers });
  check(feedRes, { 'soak feed 200': (r) => r.status === 200 });

  sleep(2);
}
```

---

### 8. Create the combined load test entry point

Create `k6/load-test.js`:

```javascript
/**
 * Combined load test entry point.
 *
 * Run a specific scenario:
 *   k6 run k6/scenarios/login.js
 *   k6 run k6/scenarios/home-feed.js
 *
 * Run all scenarios in parallel:
 *   k6 run k6/load-test.js
 *
 * Override base URL:
 *   k6 run --env BASE_URL=http://staging.example.com k6/scenarios/home-feed.js
 */

import { login as loginScenario } from './scenarios/login.js';
import { default as feedDefault, setup as feedSetup } from './scenarios/home-feed.js';

export const options = {
  scenarios: {
    login_scenario: {
      executor: 'ramping-vus',
      stages: [
        { duration: '30s', target: 20 },
        { duration: '1m',  target: 50 },
        { duration: '30s', target: 0  },
      ],
      exec: 'loginTest',
    },
    feed_scenario: {
      executor: 'ramping-vus',
      startTime: '30s',  // start after login warms up
      stages: [
        { duration: '30s', target: 30 },
        { duration: '2m',  target: 100 },
        { duration: '30s', target: 0 },
      ],
      exec: 'feedTest',
    },
  },
  thresholds: {
    'http_req_duration{endpoint:feed}': ['p(95)<800'],
    'http_req_duration{scenario:login}': ['p(95)<500'],
    'http_req_failed': ['rate<0.01'],
  },
};

export function setup() {
  return feedSetup();
}

export function loginTest() {
  loginScenario();
}

export function feedTest(data) {
  feedDefault(data);
}
```

---

### 9. Run a quick smoke load test

With the backend running at `http://localhost:8080`:

```powershell
cd C:\workspace\Instagram-Social
k6 run k6/scenarios/login.js
```

Expected output (abbreviated):

```
          /\      |‾‾| /‾‾/   /‾‾/
     /\  /  \     |  |/  /   /  /
    /  \/    \    |     (   /   ‾‾\
   /          \   |  |\  \ |  (‾)  |
  / __________ \  |__| \__\ \_____/ .io

  execution: local
     script: k6/scenarios/login.js

  scenarios: (100.00%) 1 scenario, 50 max VUs, 2m30s max duration
           * default: Up to 50 looping VUs for 2m0s

  ✓ login is 200
  ✓ token returned

  checks.........................: 100.00% ✓ 2847  ✗ 0
  http_req_duration..............: avg=42.5ms p(95)=88ms p(99)=120ms
  http_req_failed................: 0.00%   ✓ 0     ✗ 1423

  ✓ 1 thresholds passed
```

If thresholds fail, you will see `✗ 1 thresholds failed` and k6 exits with code 99.

---

### 10. Run the soak test

```powershell
k6 run k6/scenarios/soak.js
```

This runs for 10 minutes. While it runs, monitor the backend JVM in a separate terminal:

```powershell
# Watch JVM memory and thread count (requires JMX or Actuator)
curl http://localhost:8080/actuator/metrics/jvm.memory.used
curl http://localhost:8080/actuator/metrics/hikaricp.connections.active
```

If `hikaricp.connections.active` climbs to the pool maximum and stays there, you have a connection leak. If `jvm.memory.used` grows without plateau, you have a memory leak.

---

### 11. Document baseline results

Create `docs/infra/load-test.md` and record the numbers from your first run:

```markdown
# Load Test Baseline

Recorded: <date>
Stack: Docker Compose (postgres:15, Spring Boot 3.3.4, Vite dev server)
k6 version: <version>

## Scenarios

| Scenario   | VUs (peak) | Duration | p50  | p95  | p99  | Error rate |
|------------|-----------|----------|------|------|------|------------|
| Login      | 50        | 2m       | 40ms | 88ms | 120ms | 0.00%    |
| Home feed  | 100       | 3m       | 95ms | 320ms | 580ms | 0.00%   |
| Create post| 30        | 2m       | 120ms | 450ms | 720ms | 0.00%  |
| Soak (feed)| 30        | 10m      | 90ms | 280ms | 420ms | 0.00%   |

## SLO Thresholds Configured

- Login p95 ≤ 500ms
- Feed p95 ≤ 800ms
- Create post p95 ≤ 1000ms
- Error rate ≤ 1%

## Notes

- Replace placeholder numbers above with actual results from your first run.
- Re-run after each performance optimization task (TASK-10.3, TASK-10.4, TASK-10.8) and append a new table row with the date and the change made.
```

---

## Checklist

- [ ] Add a `k6/` directory with scripts for the hottest journeys (login, home-feed scroll, create post)
  - [ ] `k6/lib/auth.js` — shared login helper using `setup()` pattern
  - [ ] `k6/scenarios/login.js` — ramp-up/steady/ramp-down stages; p95 < 500ms threshold
  - [ ] `k6/scenarios/home-feed.js` — ramp-up/steady/ramp-down stages; p95 < 800ms threshold
  - [ ] `k6/scenarios/create-post.js` — ramp-up/steady/ramp-down stages; p95 < 1000ms threshold
  - [ ] `k6/load-test.js` — combined entry point for parallel scenarios
- [ ] Define stages (ramp-up → steady → spike) and thresholds tied to the SLOs from TASK-10.31
  - [ ] Each scenario has a `stages` array with ramp-up, steady-state, and ramp-down phases
  - [ ] Each scenario has `thresholds` with p95 latency and `http_req_failed` rate limits
  - [ ] `k6 run` exits with code 99 when a threshold is breached
- [ ] Run against the Docker Compose stack and capture p95/p99 + error rate
  - [ ] `k6 run k6/scenarios/login.js` completes with `✓ X thresholds passed`
  - [ ] `k6 run k6/scenarios/home-feed.js` completes with `✓ X thresholds passed`
  - [ ] `k6 run k6/scenarios/create-post.js` completes with `✓ X thresholds passed`
- [ ] Add a soak scenario (sustained load for N minutes) to surface memory leaks / connection-pool exhaustion
  - [ ] `k6/scenarios/soak.js` runs for ≥ 8 minutes at steady load
  - [ ] JVM heap and HikariCP connection metrics are observed during the run
- [ ] Document baseline numbers in `docs/infra/load-test.md` and fail the run on a threshold breach
  - [ ] `docs/infra/load-test.md` created with the table structure
  - [ ] Baseline numbers filled in from the first successful run

---

## How to Verify

Run the full load test suite against the running stack:

```powershell
cd C:\workspace\Instagram-Social
k6 run k6/scenarios/login.js
k6 run k6/scenarios/home-feed.js
k6 run k6/scenarios/create-post.js
```

Each run should produce output ending with:

```
✓ N thresholds passed
```

and exit with code 0.

To verify the threshold failure mode works, temporarily set an impossibly tight threshold:

```powershell
k6 run --env BASE_URL=http://localhost:8080 `
       -e "THRESHOLD_P95=1" `
       k6/scenarios/login.js
```

Or edit `thresholds` in the script to `['p(95)<1']` temporarily. The run should end with:

```
✗ 1 thresholds failed
```

and exit code 99. Revert the change.

---

## Notes / Gotchas

- **k6 uses JavaScript (ES6 modules), not Node.js** — k6 has its own JavaScript runtime. You cannot use Node.js modules (`require`, `fs`, `path`). Import only from k6's built-in modules (`k6/http`, `k6/metrics`, `k6`) and your own local files.

- **`setup()` runs once, `default` runs per VU** — Use `setup()` to acquire a shared auth token and pass it as the `data` argument to the `default` function. If each VU calls `login()` in `default()`, you generate unnecessary login traffic that skews the latency numbers.

- **`sleep(n)` simulates think time** — Without `sleep`, each VU hammers the API as fast as it can. This is unrealistic. 1–3 seconds of sleep per iteration models a real user who reads the response before making the next request.

- **`http_req_duration` tags** — Using `tags: { endpoint: 'feed' }` in each request lets you write separate thresholds per endpoint (`'http_req_duration{endpoint:feed}'`). Without tags, all requests from all scenarios are aggregated together, making it impossible to distinguish which endpoint exceeded the SLO.

- **Seed data prerequisite** — The `seed_user` account is created by `V2__seed_data.sql`. If your test database does not have seed data (e.g., a fresh Testcontainers database in TASK-10.39), create a test user via the registration API before running k6:
  ```powershell
  Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/v1/auth/register" `
    -ContentType "application/json" `
    -Body '{"username":"loadtest_user","email":"lt@example.com","password":"Test@1234","fullName":"Load Tester"}'
  ```

- **Running in CI** — k6 load tests are typically NOT run in the standard CI pipeline (they take minutes and generate real load). A separate CI job or a scheduled pipeline run is more appropriate. If you want to add it to CI, use the `k6 run --out json=results.json` flag to capture output and process it in a subsequent step.

- **HikariCP default pool size** — The default pool size is 10 connections. At 100 VUs hitting the feed endpoint simultaneously, each VU may need a connection. If `p99` latency spikes during the feed soak test, check `hikaricp.connections.pending` via the Actuator — you likely need to tune the pool (see [TASK-10.2](TASK-10.2-hikaricp-pool-tuning.md)).

- Official docs: [k6 — Getting started](https://k6.io/docs/get-started/running-k6/), [k6 — Thresholds](https://k6.io/docs/using-k6/thresholds/), [k6 — Scenarios](https://k6.io/docs/using-k6/scenarios/), [k6 — `setup()` and `teardown()`](https://k6.io/docs/using-k6/test-lifecycle/).
