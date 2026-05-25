# TASK-10.42 — Write a one-command smoke test script

## Overview

A smoke test is the simplest possible "is the app alive?" check — a handful of HTTP requests that together confirm the stack started correctly, the database is reachable, and the API is responding to real traffic. This task creates a short shell script (and a `make smoke` shortcut) that runs those checks automatically and exits non-zero if anything fails. Running it after every `docker compose up` or deploy takes ten seconds and replaces five minutes of manual clicking through the UI to confirm nothing is broken.

## Level

**Warm-up** · Pairs with [TASK-10.46](TASK-10.46-docker-compose.md) (full docker-compose stack).

## Why

When you bring up the full stack, it is easy for the backend to start without actually connecting to Postgres (the port is open but queries fail), or for the frontend container to serve stale files from a broken build. A smoke test exercises the actual HTTP path — from client to server to database — and fails loudly if anything in that chain is broken. The sooner you catch a broken deploy, the less time you waste debugging in the wrong place. A non-zero exit code also means the script can be dropped directly into a CI pipeline step to gate a deployment.

## Prerequisites

- Docker Desktop running, with the full stack up via `docker compose up` (see [TASK-10.46](TASK-10.46-docker-compose.md)).
- `curl` available on your machine. On Windows it ships with Git Bash and WSL; it is also included in recent Windows 10/11 builds (`curl.exe`). Verify with `curl --version`.
- A valid JWT token for the authenticated endpoint check (or a test user whose credentials you know). The script uses an environment variable so the token is not hardcoded.
- **Concepts to skim:**
  - _HTTP status codes_ — 200 OK, 401 Unauthorized, 503 Service Unavailable. The smoke script checks the status code, not the response body.
  - _`curl` exit codes_ — `curl` returns 0 on success and non-zero on network failure. Use `--fail` to also return non-zero on HTTP 4xx/5xx responses.
  - _`/actuator/health`_ — the Spring Boot Actuator health endpoint aggregates the DB, Redis, and other health contributors into a single JSON response with an overall `"status": "UP"` or `"status": "DOWN"`.

## Files to Create / Modify

```
scripts/smoke.sh          (new)
Makefile                  (modify — add smoke target)
```

## Step-by-Step

### 1. Create the `scripts/` directory

The repo root already has `backend/`, `frontend/`, and `docs/`. Add a `scripts/` directory beside them.

PowerShell:

```powershell
New-Item -ItemType Directory -Path scripts -Force
```

Bash:

```bash
mkdir -p scripts
```

### 2. Create `scripts/smoke.sh`

Create the file `scripts/smoke.sh` at the repo root level (not inside `backend/` or `frontend/`):

```bash
#!/usr/bin/env bash
# smoke.sh — fast health check for the running stack.
# Usage: ./scripts/smoke.sh [BASE_URL] [AUTH_TOKEN]
#   BASE_URL defaults to http://localhost:8080
#   AUTH_TOKEN can also be set via the SMOKE_TOKEN env var.
#
# Exit codes:
#   0 — all checks passed
#   1 — one or more checks failed

set -euo pipefail

BASE_URL="${1:-${SMOKE_BASE_URL:-http://localhost:8080}}"
TOKEN="${2:-${SMOKE_TOKEN:-}}"
FRONTEND_URL="${SMOKE_FRONTEND_URL:-http://localhost:3000}"

PASS=0
FAIL=0

# ── helpers ────────────────────────────────────────────────────────────────

check() {
  local name="$1"
  local expected_status="$2"
  shift 2
  local actual_status
  actual_status=$(curl --silent --output /dev/null --write-out "%{http_code}" "$@")
  if [ "$actual_status" = "$expected_status" ]; then
    echo "  PASS  $name  (HTTP $actual_status)"
    PASS=$((PASS + 1))
  else
    echo "  FAIL  $name  (expected HTTP $expected_status, got HTTP $actual_status)"
    FAIL=$((FAIL + 1))
  fi
}

# ── checks ─────────────────────────────────────────────────────────────────

echo ""
echo "Smoke test — $BASE_URL"
echo "──────────────────────────────────────────────────"

# 1. Actuator health — confirms Spring Boot, Postgres, and Redis are all UP
check "actuator/health" "200" \
  "$BASE_URL/actuator/health"

# 2. Unauthenticated feed — should return 401, not 500
check "GET /api/v1/feed (no token → 401)" "401" \
  "$BASE_URL/api/v1/feed"

# 3. Authenticated feed — only run when a token is provided
if [ -n "$TOKEN" ]; then
  check "GET /api/v1/feed (authenticated → 200)" "200" \
    -H "Authorization: Bearer $TOKEN" \
    "$BASE_URL/api/v1/feed"
else
  echo "  SKIP  GET /api/v1/feed (authenticated) — set SMOKE_TOKEN to enable"
fi

# 4. Frontend container — should serve the SPA shell (HTTP 200)
check "Frontend / (SPA root)" "200" \
  "$FRONTEND_URL/"

# 5. Frontend deep-link — nginx SPA fallback should return 200, not 404
check "Frontend /search (SPA deep link)" "200" \
  "$FRONTEND_URL/search"

# ── summary ────────────────────────────────────────────────────────────────

echo "──────────────────────────────────────────────────"
echo "  Passed: $PASS   Failed: $FAIL"
echo ""

if [ "$FAIL" -gt 0 ]; then
  echo "SMOKE TEST FAILED"
  exit 1
fi

echo "SMOKE TEST PASSED"
exit 0
```

Make the script executable (required on Linux/macOS and inside CI):

```bash
chmod +x scripts/smoke.sh
```

On Windows (PowerShell) `chmod` does not exist. The script is executed directly by `bash` in the Makefile, so the permission is only needed in CI (Linux runners). You can set it once via Git so it is tracked:

```powershell
git update-index --chmod=+x scripts/smoke.sh
```

### 3. Add a `smoke` target to the `Makefile`

Open `Makefile` (repo root) and add the `smoke` target. The existing targets use tabs — be careful to keep the indentation as a tab, not spaces, or `make` will error with `missing separator`.

```makefile
smoke:
	bash scripts/smoke.sh

.PHONY: dev test migrate clean smoke
```

After the edit the full `Makefile` should look like:

```makefile
dev:
	docker compose up -d
	cd backend && mvn spring-boot:run &
	cd frontend && npm run dev

test:
	cd backend && mvn test
	cd frontend && npm run test

migrate:
	cd backend && mvn flyway:migrate -Dspring.profiles.active=local

clean:
	docker compose down -v

smoke:
	bash scripts/smoke.sh

.PHONY: dev test migrate clean smoke
```

### 4. Run the smoke test

With the full stack running (see [TASK-10.46](TASK-10.46-docker-compose.md)):

```powershell
# Option A — via make (requires make installed, e.g. via Chocolatey or Scoop)
make smoke

# Option B — direct bash invocation (always works with Git Bash or WSL)
bash scripts/smoke.sh

# Option C — with an auth token
$env:SMOKE_TOKEN = "eyJhbGci..."
bash scripts/smoke.sh
```

Expected output when the stack is healthy:

```
Smoke test — http://localhost:8080
──────────────────────────────────────────────────
  PASS  actuator/health  (HTTP 200)
  PASS  GET /api/v1/feed (no token → 401)  (HTTP 401)
  SKIP  GET /api/v1/feed (authenticated) — set SMOKE_TOKEN to enable
  PASS  Frontend / (SPA root)  (HTTP 200)
  PASS  Frontend /search (SPA deep link)  (HTTP 200)
──────────────────────────────────────────────────
  Passed: 4   Failed: 0

SMOKE TEST PASSED
```

### 5. (Optional) Add the smoke check to CI after deploy

Once TASK-10.47 has the Docker images building in CI, you can add a smoke step at the end of the workflow:

```yaml
- name: Smoke test
  run: bash scripts/smoke.sh
  env:
    SMOKE_BASE_URL: http://localhost:8080
```

This will catch any image that builds successfully but fails to start or connect to the database.

## Checklist

- [ ] Create a `make smoke` target (or `scripts/smoke.sh`)
- [ ] Have it curl `/actuator/health` and one real endpoint (e.g. `GET /api/v1/feed`)
- [ ] Exit non-zero if any check fails

## How to Verify

1. Bring up the full stack:

   ```powershell
   docker compose up -d
   ```

2. Wait until all containers are healthy (see [TASK-10.46](TASK-10.46-docker-compose.md) for healthcheck details), then run:

   ```powershell
   bash scripts/smoke.sh
   ```

   Passing result: the script prints `SMOKE TEST PASSED` and exits with code 0.

3. Simulate a failure by stopping the backend container and re-running:

   ```powershell
   docker compose stop backend
   bash scripts/smoke.sh
   echo "Exit code: $LASTEXITCODE"
   ```

   Passing result: the script prints `FAIL  actuator/health`, then `SMOKE TEST FAILED`, and exits with code 1.

4. Confirm the `make` shortcut works (if `make` is installed):

   ```powershell
   make smoke
   ```

## Notes / Gotchas

- **`make` is not installed by default on Windows.** Install it via [Chocolatey](https://chocolatey.org/) (`choco install make`) or [Scoop](https://scoop.sh/) (`scoop install make`). Alternatively run `bash scripts/smoke.sh` directly — `make` is just a convenience wrapper.

- **Actuator endpoints are locked down by default in Spring Security.** If `/actuator/health` returns 401 instead of 200, add the following to `backend/src/main/resources/application.yml`:

  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: health,info,metrics
    endpoint:
      health:
        show-details: always
  ```

  And permit the path in your `SecurityConfig`:

  ```java
  .requestMatchers("/actuator/**").permitAll()
  ```

- **SPA fallback check (step 5 in the script) only works after TASK-10.45** adds the nginx `try_files` fallback. Before that, `/search` will return 404 and the check will fail — that is expected and correct.

- **`set -euo pipefail` in the script** makes the script exit immediately on any unhandled error, treats unset variables as errors, and propagates pipe failures. This is safe practice for CI scripts but means a mis-typed variable name will abort the script rather than silently using an empty value.

- **Do not hardcode tokens in the script.** Always pass them via environment variables (`SMOKE_TOKEN`) or CI secrets. A token hardcoded in a script will eventually be committed to Git.

- Related tasks: [TASK-10.46](TASK-10.46-docker-compose.md), [TASK-10.47](TASK-10.47-cicd-docker-build-push.md), [TASK-10.49](TASK-10.49-troubleshooting-runbook.md).

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **What a smoke test is** — a fast "is it alive?" check after deploy — https://en.wikipedia.org/wiki/Smoke_testing_(software)
- **curl for API checks** — status codes, headers, exit codes in scripts — https://curl.se/docs/manual.html
- **Health endpoints** — `/actuator/health` as the smoke-test target — https://spring.io/guides/gs/actuator-service/

### Official docs (code reference)
- **curl documentation** — https://curl.se/docs/
- **Bash reference manual** — https://www.gnu.org/software/bash/manual/bash.html
