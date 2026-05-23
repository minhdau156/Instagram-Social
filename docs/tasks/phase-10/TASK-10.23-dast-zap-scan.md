# TASK-10.23 — DAST scan in CI (OWASP ZAP baseline)

## Overview

Add a Dynamic Application Security Testing (DAST) job to the GitHub Actions CI pipeline. The job boots the full Docker Compose stack, runs an OWASP ZAP baseline scan against the running backend, and fails the build on any `High`-risk alert. The HTML report is saved as a CI artifact so you can read exactly what ZAP found. Unlike [TASK-10.19 (OWASP dependency check)](TASK-10.19-owasp-dependency-check.md), which inspects the supply chain at build time, this scan probes the live application from the outside — the same vantage point an attacker has.

---

## Level

**Core** — Pairs with [TASK-10.19 (OWASP dependency check)](TASK-10.19-owasp-dependency-check.md). Together they give you both supply-chain scanning (SCA) and runtime scanning (DAST).

---

## Why

Dependency scanning (TASK-10.19) can tell you that `spring-security:6.0.1` has a known CVE. It cannot tell you that your app exposes a Swagger UI endpoint with no authentication, that the login endpoint reflects error messages verbatim, or that a missing CORS header lets any website make authenticated API calls on your user's behalf. Those findings only appear when something probes the live, running application. The ZAP baseline scan does exactly that in about three minutes: it spiders the app, fires a range of passive and light active probes, and reports anything that deviates from security best practices. It is not a full penetration test, but it catches a well-known set of low-hanging issues automatically on every CI run.

---

## Prerequisites

- Docker available in the CI runner (GitHub Actions `ubuntu-latest` includes Docker).
- A `docker-compose.yml` at the repo root that can boot the full stack (or at minimum the backend + database). If this does not exist yet, create a minimal version as part of this task or coordinate with [TASK-10.46 (Full docker-compose.yml)](TASK-10.46-docker-compose.md).
- The backend must start successfully in CI (the existing `backend-ci` job confirms this).
- **Concept gloss:**
  - **DAST** — Dynamic Application Security Testing. Testing a running application by sending it HTTP requests and observing responses, the same way an attacker would.
  - **ZAP baseline scan** — a read-only mode of ZAP that does not attempt to exploit vulnerabilities; it only looks for passive indicators of known issues (missing headers, verbose errors, open redirects, etc.).
  - **`zaproxy/zap-stable` Docker image** — the official OWASP ZAP Docker image. The `zap-baseline.py` script inside it runs the baseline scan.
  - **Alert risk levels** — ZAP classifies each finding as `High`, `Medium`, `Low`, or `Informational`. The build gate here is `High`.
  - **Allowlist / false-positive tuning** — ZAP rules that fire on known-safe findings are suppressed in a `.zap/rules.tsv` file committed to the repo.

---

## Files to Create / Modify

```
.github/workflows/ci.yml            (modify — add zap-scan job)
.zap/rules.tsv                      (new — false-positive allowlist)
docker-compose.yml                  (modify or create — needed to boot the stack in CI)
docs/infra/zap-local-scan.md        (new — how to run the scan locally)
```

---

## Step-by-Step

### 1. Create a minimal `docker-compose.yml` (if it doesn't exist)

If [TASK-10.46](TASK-10.46-docker-compose.md) is already done, skip this step. Otherwise create a minimal version at the repo root that starts just the backend and its database — enough for ZAP to probe:

```yaml
# docker-compose.yml (minimal, for CI/ZAP use — expand in TASK-10.46)
version: '3.9'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_USER: instagram
      POSTGRES_PASSWORD: changeme
      POSTGRES_DB: instagram
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U instagram"]
      interval: 5s
      timeout: 3s
      retries: 10

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile    # created in TASK-10.44
    environment:
      SPRING_PROFILES_ACTIVE: local
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/instagram
      SPRING_DATASOURCE_USERNAME: instagram
      SPRING_DATASOURCE_PASSWORD: changeme
      JWT_RSA_PRIVATE_KEY: ${JWT_RSA_PRIVATE_KEY}
      JWT_RSA_PUBLIC_KEY: ${JWT_RSA_PUBLIC_KEY}
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: minioadmin
      MINIO_SECRET_KEY: minioadmin
      MINIO_BUCKET: instagram-media
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 12

  minio:
    image: minio/minio:latest
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports:
      - "9000:9000"
```

### 2. Create the ZAP false-positive allowlist

Create `.zap/rules.tsv`. Each line suppresses one ZAP rule ID with a reason. The most common false positive in a Spring Boot + Swagger app is the "X-Content-Type-Options header missing" alert fired against the Swagger UI (which sets its own headers differently):

```tsv
# .zap/rules.tsv
# Format: RULE_ID<TAB>IGNORE<TAB>reason
#
# 10021  X-Content-Type-Options Header Missing
#        Swagger UI (/swagger-ui/**) sets this differently; the API endpoints
#        set it correctly (TASK-10.14). Ignoring Swagger UI paths only.
10021	IGNORE	Swagger UI sets content-type-options differently — API endpoints are covered

# 10015  Incomplete or No Cache-control and Pragma HTTP Header Set
#        API responses are application/json; caching is handled at the CDN layer (TASK-10.5).
10015	IGNORE	JSON API responses; cache control handled at CDN layer
```

Add more suppressions here as you identify false positives from the first scan run. Every suppression must have a reason — this file is your audit trail.

### 3. Add the `zap-scan` job to `.github/workflows/ci.yml`

Open `.github/workflows/ci.yml` and add a new job after `backend-ci` and `frontend-ci`:

```yaml
  zap-scan:
    name: OWASP ZAP Baseline Scan
    runs-on: ubuntu-latest
    # Only run after the backend successfully builds and tests pass
    needs: [backend-ci]

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Build backend image
        run: docker build -t instagram-backend:ci ./backend

      - name: Start the stack
        env:
          JWT_RSA_PRIVATE_KEY: ${{ secrets.JWT_RSA_PRIVATE_KEY }}
          JWT_RSA_PUBLIC_KEY:  ${{ secrets.JWT_RSA_PUBLIC_KEY }}
        run: |
          docker compose up -d
          echo "Waiting for backend to become healthy..."
          timeout 120 bash -c \
            'until curl -sf http://localhost:8080/actuator/health; do sleep 3; done'

      - name: Run OWASP ZAP baseline scan
        run: |
          docker run --rm \
            --network host \
            -v ${{ github.workspace }}/.zap:/zap/wrk/.zap:ro \
            -v ${{ github.workspace }}/zap-report:/zap/wrk/report \
            zaproxy/zap-stable \
            zap-baseline.py \
              -t http://localhost:8080 \
              -c /zap/wrk/.zap/rules.tsv \
              -r /zap/wrk/report/zap-report.html \
              -I   \
              -l WARN
          # Exit codes: 0 = no alerts, 1 = warnings only, 2 = high alerts (fails the build)
          # We intentionally do NOT use -I (ignore warnings) here — the exit code handles it

      - name: Upload ZAP HTML report
        if: always()    # upload even if the scan failed
        uses: actions/upload-artifact@v4
        with:
          name: zap-report
          path: zap-report/zap-report.html
          retention-days: 30

      - name: Tear down stack
        if: always()
        run: docker compose down -v
```

> **Note on exit codes:** `zap-baseline.py` exits with `0` (no alerts above the threshold), `1` (warnings present but no `High` alerts — CI passes with a warning), or `2` (`High` alert found — CI fails). The `-l WARN` flag sets the minimum log level; without `-I` (ignore warnings), exit code `1` still passes the CI job. Only exit code `2` fails the build.

### 4. Create `docs/infra/zap-local-scan.md`

Create the directory if it doesn't exist and write the local run guide:

```powershell
New-Item -ItemType Directory -Force -Path "docs\infra"
```

Contents of `docs/infra/zap-local-scan.md`:

```markdown
# Running the OWASP ZAP Baseline Scan Locally

## Prerequisites

- Docker Desktop running.
- The backend stack booted: `docker compose up -d`.
- The backend is healthy: `curl http://localhost:8080/actuator/health`.

## Run the scan

```powershell
# Create the output directory
New-Item -ItemType Directory -Force -Path zap-report

# Run ZAP baseline against the local backend
docker run --rm `
  --network host `
  -v "${PWD}\.zap:/zap/wrk/.zap:ro" `
  -v "${PWD}\zap-report:/zap/wrk/report" `
  zaproxy/zap-stable `
  zap-baseline.py `
    -t http://localhost:8080 `
    -c /zap/wrk/.zap/rules.tsv `
    -r /zap/wrk/report/zap-report.html `
    -l WARN
```

## View the report

```powershell
Start-Process "zap-report\zap-report.html"
```

## Interpreting results

| Risk | Build impact | Action |
|------|--------------|--------|
| High | Fails CI     | Fix before merge |
| Medium | Warning only | Fix soon; document if false positive |
| Low | Informational | Fix at discretion |

To suppress a false positive, add a line to `.zap/rules.tsv` with the rule ID and a reason.
Find the rule ID in the HTML report's "Alert" column.
```
```

### 5. Run the scan locally and tune the allowlist

Before pushing to CI, run the scan locally (Step 4), read the HTML report, and add suppressions to `.zap/rules.tsv` for any confirmed false positives. Common ones for this stack:

- Swagger UI generates self-referencing links that trigger "Application Error Disclosure" rules.
- The H2 console (if exposed in dev) triggers "Web Browser XSS Protection Not Enabled".
- OPTIONS preflight responses may trigger missing-header alerts.

Each suppression needs a rule ID (from the HTML report) and a one-sentence justification.

---

## Checklist

- [ ] Add a CI job that boots the Docker Compose stack and runs the `zaproxy/zap-baseline` scan against the backend
  - [ ] `needs: [backend-ci]` so ZAP runs only after tests pass
  - [ ] Stack health-check before starting ZAP
  - [ ] `--network host` so ZAP container reaches `localhost:8080`
- [ ] Tune the rule set / allowlist known-safe findings so the job is not perpetually red
  - [ ] `.zap/rules.tsv` created with initial suppressions for Swagger UI false positives
  - [ ] Every suppression has a written reason
- [ ] Fail the build on `High` risk alerts; warn on `Medium`
  - [ ] `zap-baseline.py` exit code `2` (High alerts) fails the job
  - [ ] Exit code `1` (Medium/Low only) passes with warning
- [ ] Upload the ZAP HTML report as a CI artifact
  - [ ] `actions/upload-artifact@v4` with `if: always()`
  - [ ] `retention-days: 30`
- [ ] Document how to run the same scan locally
  - [ ] `docs/infra/zap-local-scan.md` created

---

## How to Verify

**CI job passes (no High alerts):**

1. Push a commit to any branch with a PR to `main`.
2. Open GitHub Actions → the `OWASP ZAP Baseline Scan` job.
3. Passing result: all steps green; the `Upload ZAP HTML report` step shows the artifact uploaded.

**CI job fails when a High alert is present (confirm the gate works):**

Temporarily comment out the `X-Frame-Options` header in `SecurityConfig` (removing a key header will trigger a ZAP `High` alert for "Clickjacking: X-Frame-Options Header Not Set"), push, and confirm the job fails with exit code `2`. Re-add the header.

**Local report opens in a browser:**

```powershell
# After running the local scan (docs/infra/zap-local-scan.md)
Test-Path "zap-report\zap-report.html"   # Expected: True
Start-Process "zap-report\zap-report.html"
# Expected: browser opens showing the ZAP scan results
```

---

## Notes / Gotchas

**The ZAP baseline scan is NOT a full penetration test.**
The baseline scan fires passive and light-active probes but does not attempt to exploit vulnerabilities. It will not find SQL injection that requires carefully crafted payloads, or business logic flaws. Use it as a safety net for obvious misconfigurations (missing headers, verbose errors, open redirects); do not rely on it as a substitute for a manual security review.

**The first scan run will be slow in CI.**
The `zaproxy/zap-stable` Docker image is roughly 1 GB. GitHub Actions caches Docker images between runs on the same runner, but the first pull is slow. Subsequent runs are faster.

**`--network host` is required on Linux runners.**
The ZAP container needs to reach `localhost:8080` (the backend). On Linux (the CI runner), `--network host` maps the container's network to the host's, making `localhost` work. On macOS or Windows Docker Desktop, `host.docker.internal` is required instead of `localhost`. The CI runner is Linux, so the above command is correct for CI. The local Windows PowerShell version in `zap-local-scan.md` uses `host.docker.internal`.

**`zap-baseline.py` returns exit code `1` even on Medium alerts.**
If CI is failing on Medium alerts that you consider acceptable, add them to `.zap/rules.tsv`. Do not raise the ZAP threshold (`-l`) from `WARN` to `FAIL` — that would suppress all Medium findings from the report output entirely, making the report less informative.

**Swagger UI is a common source of false positives.**
The Swagger UI serves HTML with inline scripts, which triggers "Anti-CSRF Tokens Check" and "Content Security Policy Not Set" rules. Suppress these for the `/swagger-ui/**` and `/v3/api-docs/**` paths, not for the entire site.

**Related tasks:**
- [TASK-10.19 (OWASP dependency check)](TASK-10.19-owasp-dependency-check.md) — companion supply-chain scanner.
- [TASK-10.14 (Security headers)](TASK-10.14-security-headers.md) — many ZAP High alerts are directly caused by missing security headers; complete that task first to keep the ZAP report clean.
- [TASK-10.46 (Full docker-compose.yml)](TASK-10.46-docker-compose.md) — the compose file this job boots.

**Reference docs:**
- [OWASP ZAP — zap-baseline.py](https://www.zaproxy.org/docs/docker/baseline-scan/)
- [OWASP ZAP — Docker images](https://www.zaproxy.org/docs/docker/)
- [OWASP ZAP — Scan rules](https://www.zaproxy.org/docs/alerts/)
