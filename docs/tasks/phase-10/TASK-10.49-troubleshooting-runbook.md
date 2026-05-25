# TASK-10.49 — Troubleshooting runbook for common failures

## Overview

Create a `docs/troubleshooting.md` file that documents the most common local-dev and runtime failures the team encounters. Each failure is written as a **Symptom → Cause → Fix** entry so anyone can scan the table and unblock themselves in under two minutes. The runbook is linked from the project README so it is the first place new contributors look when the stack does not start.

This task produces documentation only — no source code is modified.

---

## Level

Warm-up · Pairs with [TASK-10.42 — Write a one-command smoke test script](TASK-10.42-smoke-test-script.md) and [TASK-10.46 — Full docker-compose.yml](TASK-10.46-docker-compose.md)

---

## Why

When the stack refuses to boot, the instinct is to search the internet or ask a teammate. But the real causes are almost always the same small set of problems: a port is already bound, Postgres is not yet accepting connections, Flyway has a checksum conflict, or an environment variable is missing. Documenting these once means the second person to hit a problem can fix it in thirty seconds instead of thirty minutes. A runbook also teaches you *why* each failure happens, not just how to click past it.

---

## Prerequisites

- [TASK-10.42](TASK-10.42-smoke-test-script.md) — the `make smoke` target (or equivalent health-check one-liner) is referenced in the runbook entries.
- [TASK-10.46](TASK-10.46-docker-compose.md) — the full `docker-compose.yml` (postgres, minio, redis, backend, frontend) must exist so you can verify the service-health checks described below.
- The backend runs via `cd backend && mvn spring-boot:run` (or via Docker Compose).
- **Concepts to skim:**
  - **Port binding** — only one process can own a TCP port at a time; `netstat` / `Get-NetTCPConnection` shows what is already bound.
  - **Flyway checksum** — Flyway hashes each migration file at first run; if you edit the file afterward the hash changes and Flyway refuses to start.
  - **JWT / OAuth2 env vars** — the app reads `JWT_SECRET`, `GOOGLE_CLIENT_ID`, and `GOOGLE_CLIENT_SECRET` from the environment (see `application.yml`). Missing values fall back to developer defaults but OAuth2 flows will not work.
  - **CORS** — the browser blocks a cross-origin request unless the server includes the correct `Access-Control-Allow-Origin` header. The backend allows `http://localhost:5173` by default.

---

## Files to Create / Modify

```
docs/troubleshooting.md                      (new)
README.md                                    (modify — add link to docs/troubleshooting.md)
```

---

## Step-by-Step

### 1. Create `docs/troubleshooting.md`

Create a new file at `docs/troubleshooting.md`. The sections below give the exact content to include. Use the **Symptom → Cause → Fix** format throughout — write each entry as a level-3 heading followed by a small table or fenced block. Copy the content below directly.

```markdown
# Troubleshooting — Common Failures

> Quick lookup table for the most common local-dev and runtime failures.
> Each entry follows the **Symptom → Cause → Fix** pattern.
> Run `make smoke` (TASK-10.42) after any fix to confirm all services are healthy.

---

## Port already in use (8080 / 5432 / 9000)

**Symptom**
Backend fails to start with:
```
Web server failed to start. Port 8080 was already in use.
```
or Docker Compose exits immediately with `Error starting userland proxy: listen tcp4 0.0.0.0:5432`.

**Cause**
Another process is bound to the port. Common culprits: a previous `mvn spring-boot:run` left running in another terminal, a system Postgres install on 5432, or a leftover Docker container.

**Fix**

PowerShell — find and kill the process holding port 8080:
```powershell
# Find the PID owning port 8080
Get-NetTCPConnection -LocalPort 8080 -State Listen | Select-Object LocalPort, OwningProcess

# Kill it (replace 12345 with the actual PID)
Stop-Process -Id 12345 -Force
```

Git Bash / WSL:
```bash
# Find the process
lsof -i :8080

# Kill it (replace 12345 with the PID)
kill -9 12345
```

For Docker ports (5432, 9000, 9001, 6379):
```powershell
# Stop all compose services and remove port bindings
docker compose down
```

Then restart with `docker compose up -d`.

---

## Postgres "connection refused" on backend boot

**Symptom**
Spring Boot starts but immediately logs:
```
HikariPool-1 - Exception during pool initialization.
Connection to localhost:5432 refused. Check that the hostname and port are correct.
```

**Cause**
The Postgres container has not finished its health check yet, or Docker Compose was not started before `mvn spring-boot:run`.

**Fix**

1. Start infrastructure services first, then the backend:
```powershell
# Start Postgres (and MinIO, Redis) in the background
docker compose up -d

# Wait until Postgres reports healthy
docker compose ps

# Then start the backend
cd backend; mvn spring-boot:run
```

2. If the container reports `starting` instead of `healthy`, wait a few seconds and re-check:
```powershell
docker compose ps
```

The `healthcheck` in `docker-compose.yml` polls `pg_isready` every five seconds. The backend should only be started after the postgres service shows `healthy`.

---

## Flyway checksum mismatch

**Symptom**
Backend fails to start with:
```
FlywayException: Validate failed:
Migration checksum mismatch for migration version 1
-> Applied to database : 123456789
-> Resolved locally    : 987654321
```

**Cause**
A Flyway migration file (e.g. `V1__initial_schema.sql`) was edited after it was already applied to the local database. Flyway stores the checksum of each file at apply-time and refuses to continue if the file has since changed.

**Fix**

> **Never edit a migration file that has already been applied.** If you need to change the schema, create a new migration file with the next version number (e.g. `V2__add_column.sql`).

For local development only — if the local database data is disposable:
```powershell
# Wipe the local database volume and re-apply all migrations from scratch
docker compose down -v
docker compose up -d
# Wait for healthy, then start backend
cd backend; mvn spring-boot:run
```

If you must repair without dropping data (advanced):
```powershell
cd backend
mvn flyway:repair -Dspring.profiles.active=local
```

`flyway:repair` removes the failed migration record from the `flyway_schema_history` table and recalculates checksums. Only use it if you understand which migration is broken and why.

---

## Missing JWT / OAuth2 environment variables

**Symptom (JWT)**
The backend starts but every API call returns `401 Unauthorized`, or token verification fails with:
```
io.jsonwebtoken.security.WeakKeyException: The specified key byte array is 43 bits
```

**Symptom (OAuth2)**
Clicking "Sign in with Google" redirects to an error page:
```
Error 401: invalid_client
```

**Cause**
The app reads `JWT_SECRET`, `GOOGLE_CLIENT_ID`, and `GOOGLE_CLIENT_SECRET` from environment variables (see `application.yml`). In local development these fall back to weak defaults, which JJWT rejects if the secret is too short for the chosen algorithm. For OAuth2, Google's servers reject requests with placeholder credentials.

**Fix (JWT)**

Set a long enough secret in your shell before starting the backend:
```powershell
$env:JWT_SECRET = "a-very-long-secret-at-least-32-chars-for-HS256"
cd backend; mvn spring-boot:run
```

Or add it to a `.env` file at the repo root (never commit `.env` — it is in `.gitignore`):
```
JWT_SECRET=a-very-long-secret-at-least-32-chars-for-HS256
```

**Fix (OAuth2)**
Register an OAuth2 application in the [Google Cloud Console](https://console.cloud.google.com/), add `http://localhost:8080/login/oauth2/code/google` as an authorized redirect URI, and set the credentials:
```powershell
$env:GOOGLE_CLIENT_ID     = "your-client-id.apps.googleusercontent.com"
$env:GOOGLE_CLIENT_SECRET = "your-client-secret"
```

---

## CORS error from the frontend

**Symptom**
The browser console shows:
```
Access to XMLHttpRequest at 'http://localhost:8080/api/v1/...' from origin 'http://localhost:5173'
has been blocked by CORS policy: No 'Access-Control-Allow-Origin' header is present.
```

**Cause**
The backend's `CorsConfig` allows `http://localhost:5173` by default. This error appears when:
- The frontend is running on a different port (e.g. Vite picked `5174` because 5173 was busy).
- The backend is running behind a proxy that strips CORS headers.
- A custom `SecurityConfig` change accidentally removed the CORS configuration source.

**Fix**

1. Check which port Vite actually started on — look for `Local: http://localhost:XXXX` in the Vite terminal.
2. If the port is not 5173, set the env var before starting the backend:
```powershell
$env:FRONTEND_URL = "http://localhost:5174"
cd backend; mvn spring-boot:run
```
3. Confirm CORS headers are present on a preflight request:
```powershell
curl -si -X OPTIONS http://localhost:8080/api/v1/posts `
  -H "Origin: http://localhost:5173" `
  -H "Access-Control-Request-Method: POST" | Select-String "access-control"
```

Expected output:
```
access-control-allow-origin: http://localhost:5173
access-control-allow-methods: GET,POST,PUT,DELETE,PATCH,OPTIONS
```

---

## MinIO bucket not created

**Symptom**
Uploading a photo returns `500 Internal Server Error`, and the backend logs show:
```
io.minio.errors.ErrorResponseException: The specified bucket does not exist
```

**Cause**
MinIO starts empty. The `instagram-media` bucket must be created before the first upload. The application does not auto-create it on startup.

**Fix**

Option A — MinIO Console (easiest):
1. Open `http://localhost:9001` in your browser.
2. Log in with `minioadmin` / `minioadmin` (or the values from your `.env`).
3. Click **Buckets → Create Bucket**, name it `instagram-media`, and click **Create**.

Option B — MinIO Client (`mc`) from the terminal:
```powershell
# Add the local MinIO server as an alias
mc alias set local http://localhost:9000 minioadmin minioadmin

# Create the bucket
mc mb local/instagram-media

# Verify
mc ls local
```

Option C — `docker exec` if `mc` is not installed locally:
```powershell
docker exec -it $(docker compose ps -q minio) sh -c `
  "mc alias set local http://localhost:9000 minioadmin minioadmin && mc mb local/instagram-media"
```

---

## How to check all services are healthy at once

Run the smoke test introduced in TASK-10.42:

```powershell
make smoke
```

Or, if `make` is not available on Windows, run each check manually:
```powershell
# Postgres
docker compose exec postgres pg_isready -U postgres

# MinIO
curl -sf http://localhost:9000/minio/health/live && Write-Host "MinIO OK"

# Redis
docker compose exec redis redis-cli ping

# Backend
curl -sf http://localhost:8080/actuator/health | ConvertFrom-Json | Select-Object -ExpandProperty status

# Frontend
curl -sf http://localhost:5173 | Select-String "Instagram" | ForEach-Object { "Frontend OK" }
```

All commands should return `ready`, `OK`, `PONG`, `UP`, or a non-empty HTML page respectively.

---

## How to read a heap dump

See [TASK-10.51](../tasks/phase-10/TASK-10.51-oom-heap-dump-lab.md) for the full hands-on lab.

Quick reference:
- **Enable auto-dump on OOM:** add `-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=./heapdump.hprof` to JVM flags.
- **Where it lands:** in the directory where the JVM was started (the project root when using `mvn spring-boot:run`), or in `HeapDumpPath` if set.
- **How to open:** [Eclipse MAT](https://eclipse.dev/mat/) → File → Open Heap Dump → run the **Leak Suspects** or **Dominator Tree** report.
- **What to look for:** the object with the largest **Retained Heap** in the Dominator Tree is the root of the leak.
```

### 2. Link the runbook from `README.md`

Open `README.md` (project root). Add a line to the existing content that links to the troubleshooting guide. The current README is minimal:

```markdown
# Instagram-Social-

![CI](https://github.com/minhdau156/Instagram-Social-/actions/workflows/ci.yml/badge.svg)
```

Add the following after the CI badge line:

```markdown
## Documentation

| Doc | Description |
|-----|-------------|
| [docs/troubleshooting.md](docs/troubleshooting.md) | Symptom → Cause → Fix for common local-dev failures |
| [docs/diagrams.md](docs/diagrams.md) | Architecture and data-model diagrams (Mermaid) |
| [docs/plan.md](docs/plan.md) | Phased development roadmap |
```

### 3. Verify the six failure categories are covered

Before committing, scan `docs/troubleshooting.md` and confirm every required category has an entry. Use this PowerShell one-liner:

```powershell
$required = "Port already in use","Postgres","Flyway","JWT","CORS","MinIO bucket"
$content  = Get-Content docs/troubleshooting.md -Raw
$required | ForEach-Object {
    $label = $_
    if ($content -match [regex]::Escape($label)) {
        Write-Host "FOUND:   $label"
    } else {
        Write-Host "MISSING: $label"
    }
}
```

All six lines must print `FOUND`.

---

## Checklist

- [ ] Create `docs/troubleshooting.md` with a **Symptom → Cause → Fix** table
- [ ] Cover at least:
  - [ ] Port already in use (`8080` / `5432` / `9000`)
  - [ ] Postgres "connection refused" on boot
  - [ ] Flyway checksum mismatch
  - [ ] Missing JWT / OAuth2 env vars
  - [ ] CORS error from the frontend
  - [ ] MinIO bucket not created
- [ ] Note the one-liner that checks each service is healthy (reuse the `make smoke` check from TASK-10.42)
- [ ] Link the runbook from `README.md`

---

## How to Verify

**1. File exists and has content**

```powershell
Test-Path docs/troubleshooting.md
(Get-Content docs/troubleshooting.md).Count -gt 50
```

Both must return `True`.

**2. All six failure categories are present**

```powershell
$required = "Port already in use","Postgres","Flyway","JWT","CORS","MinIO bucket"
$content  = Get-Content docs/troubleshooting.md -Raw
$missing  = $required | Where-Object { $content -notmatch [regex]::Escape($_) }
if ($missing) { Write-Host "MISSING: $($missing -join ', ')" } else { Write-Host "All categories present." }
```

Expected output: `All categories present.`

**3. README links to the runbook**

```powershell
Get-Content README.md | Select-String "troubleshooting"
```

Expected output: a line containing `troubleshooting.md`.

**4. Smoke-test reference is present**

```powershell
Get-Content docs/troubleshooting.md | Select-String "make smoke"
```

Expected output: at least one matching line.

**Passing result:** All four checks pass, the Markdown renders without errors in a GitHub preview or VS Code Markdown preview (no broken links, no unmatched code fences), and a teammate unfamiliar with the project can follow the instructions for at least one entry from scratch.

---

## Notes / Gotchas

**"My Flyway error says 'validate failed' but I did not edit any file."**
Git's line-ending conversion (CRLF vs LF) can silently change a migration file's bytes, which changes the checksum. Configure your repo to preserve LF endings in `.gitattributes`:
```
*.sql text eol=lf
```

**"The CORS fix shows the right header in `curl` but the browser still blocks the request."**
Browsers cache preflight responses. Hard-refresh (`Ctrl+Shift+R`) or open a new incognito window to bypass the preflight cache.

**"I cannot install `mc` (MinIO Client) on my machine."**
Use the MinIO Console at `http://localhost:9001` — it does everything `mc` can do through a web UI. No installation required.

**"Port 9001 is also busy."**
The MinIO Console binds to 9001. If something else owns that port, change the host-side mapping in `docker-compose.yml` (e.g. `"9002:9001"`) and access the Console on `http://localhost:9002` instead. The API port 9000 must remain 9000 for the backend's `MINIO_ENDPOINT` setting to work without changes.

**"The runbook is getting long — should I split it into multiple files?"**
Keep it in one file while the project is small. A single file is easier to `Ctrl+F` through. Split by topic only when the file exceeds roughly 300 lines.

**Related tasks:**
- [TASK-10.42](TASK-10.42-smoke-test-script.md) — the `make smoke` one-liner referenced throughout
- [TASK-10.46](TASK-10.46-docker-compose.md) — the Docker Compose services described in the port and health-check entries
- [TASK-10.51](TASK-10.51-oom-heap-dump-lab.md) — the heap-dump entry at the bottom of the runbook
- [TASK-10.15](TASK-10.15-secrets-hygiene.md) — deeper treatment of secrets and env-var hygiene

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Incident response & runbooks** — the SRE approach to handling outages — https://sre.google/workbook/incident-response/
- **Writing effective runbooks** — symptom → diagnosis → fix, written for 3 a.m. — https://www.atlassian.com/incident-management/devops/runbooks
- **Reading logs & stack traces fast** — find the first real error, not the last — https://www.slf4j.org/manual.html

### Official docs (code reference)
- **Google SRE Workbook** — https://sre.google/workbook/table-of-contents/
- **Atlassian incident management** — https://www.atlassian.com/incident-management
