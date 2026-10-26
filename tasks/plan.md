# Implementation Plan: TASK-10.54 — Production Deployment (AWS EC2 + Grafana Cloud)

## Overview

Deploy the app to a single AWS EC2 `t4g.small` (Graviton/ARM) instance running Postgres, Redis, MinIO, backend, frontend, and Caddy, with all observability (metrics, logs, traces) routed to Grafana Cloud's free tier. This plan covers the full dependency chain: CI multi-arch builds → the one small backend change needed for Grafana Cloud auth → the deploy artifacts → cloud provisioning → deploy → verify → document.

## Current State (verified against the repo, not the task doc)

| Piece | Status | Location |
|---|---|---|
| `backend-ci` / `frontend-ci` / `docker-publish` (x86 only) | ✅ exists | `.github/workflows/ci.yml` |
| `docker/setup-buildx-action@v3` already in `docker-publish` | ✅ exists | `.github/workflows/ci.yml:84` |
| Multi-arch (`platforms:` / QEMU) | ❌ missing | `.github/workflows/ci.yml` |
| Traces exported via OTLP, env var `OTEL_ENDPOINT` | ✅ exists, **not Zipkin** (the task doc originally assumed Zipkin — wrong, corrected) | `backend/src/main/resources/application.yml:30-32` |
| Logs pushed directly to Loki via `loki-logback-appender` 1.5.2, env var `LOKI_URL` | ✅ exists, **no auth sent** | `backend/src/main/resources/logback-spring.xml` |
| `/actuator/prometheus` scrape endpoint | ✅ exists | `backend/src/main/resources/application.yml` (TASK-10.27) |
| `deploy/` directory, prod compose file, Caddyfile, Alloy config | ❌ none exist yet | — |
| `docs/infra/deployment.md` | ❌ doesn't exist | — |

## Architecture Decisions (deviations from the task doc, now corrected in TASK-10.54.md itself)

- **No Alloy relay for traces or logs.** The task doc originally assumed the backend exports traces via Zipkin and that logs need an agent tailing Docker's log files. Neither matches the code: traces already use Spring Boot's built-in OTLP exporter (`management.otlp.tracing.endpoint`), and logs already push straight to Loki from `logback-spring.xml`. Both can point directly at Grafana Cloud with env vars; Alloy's only remaining job is scraping `/actuator/prometheus` and remote-writing it to Mimir, since Micrometer's Prometheus registry is pull-only and nothing else bridges push-based Grafana Cloud metrics ingestion without adding a new dependency (`micrometer-registry-otlp`), which is out of scope for a deployment-only task.
- **One small, additive backend change is unavoidable:** Grafana Cloud requires Basic Auth on its Loki push endpoint, and the current `!local` Loki appender sends none. Adding `<auth><username>/<password></auth>` with empty-default env vars (`LOKI_USER`/`LOKI_API_KEY`) is backward-compatible — any deployment that doesn't set those vars keeps pushing unauthenticated, exactly as today. The `local` profile's appender is untouched.
- **Trace auth needs no file change at all** — `management.otlp.tracing.headers` is a Spring Boot property that binds via environment variable (`MANAGEMENT_OTLP_TRACING_HEADERS_AUTHORIZATION`) with no code or config file edit required.
- **EC2, not Oracle Cloud.** Earlier conversation considered Oracle's Always Free ARM tier; the user settled on AWS EC2 (`t4g.small`, also ARM/Graviton) for app + DB + MinIO, with Grafana Cloud handling observability. This plan targets that combination.

## Task List

### Phase 1 — CI: multi-arch images

#### Task 1: Add multi-arch (`arm64`) builds to `docker-publish`

**Description:** Add a QEMU setup step and `platforms: linux/amd64,linux/arm64` to both image build steps in the existing `docker-publish` job, so GHCR produces images that run natively on Graviton.

**Acceptance criteria:**
- [ ] `docker/setup-qemu-action@v3` step added before the existing Buildx step
- [ ] Both `docker/build-push-action@v6` steps gain `platforms: linux/amd64,linux/arm64`
- [ ] `backend-ci`, `frontend-ci`, and the frontend's existing `VITE_API_URL=http://localhost:8080` build-arg are untouched

**Verification:**
- [ ] YAML validates: `python -c "import yaml; yaml.safe_load(open('.github/workflows/ci.yml'))"`
- [ ] Manual diff review: only the `docker-publish` job changed
- [ ] Manual (post-push, human): push to `main`, confirm in the Actions tab that both images build for both platforms, and `docker manifest inspect ghcr.io/minhdau156/instagram-backend:latest` lists an `arm64` entry

**Dependencies:** None

**Files:** `.github/workflows/ci.yml` (modify)

**Estimated scope:** XS (1 file, additive)

---

### Phase 2 — Backend: minimal additive change for Grafana Cloud auth

#### Task 2: Add Basic Auth + Grafana Cloud limits to the production Loki appender

**Description:** In `logback-spring.xml`, add an `<auth>` block (env-driven, empty-default) plus the timeout/batch settings Grafana Cloud requires, to the `!local` profile's `LOKI` appender only.

**Verified against loki4j's own ["Sending logs to Grafana Cloud"](https://loki4j.github.io/loki-logback-appender/docs/grafanacloud) guide** (confirmed via web search during planning, not assumed): the `<auth>` tag names are correct, and Grafana Cloud additionally requires `requestTimeoutMs: 15000` and caps batch size at `65536` bytes — below loki4j's default, so without the explicit `<batch><maxBytes>` cap, log batches will intermittently and silently fail to ship once volume grows past 64 KB.

**Acceptance criteria:**
- [ ] `!local` `LOKI` appender's `<http>` block gains `<auth><username>${LOKI_USER:-}</username><password>${LOKI_API_KEY:-}</password></auth>` and `<requestTimeoutMs>15000</requestTimeoutMs>`
- [ ] `!local` `LOKI` appender gains a `<batch><maxBytes>65536</maxBytes></batch>` block
- [ ] `local` profile's `LOKI` appender is byte-for-byte unchanged (no auth, no timeout change, no batch cap — self-hosted Loki has no such limit)
- [ ] No other appender config changes

**Verification:**
- [ ] `cd backend && mvn -q -DskipTests package` builds successfully
- [ ] Manual: run with `SPRING_PROFILES_ACTIVE=local` against the existing local `docker-compose.yml` Loki stack (TASK-10.30) and confirm logs still arrive unauthenticated, unchanged from today
- [ ] Manual (post-deploy): confirm no 401s or 413 (payload too large) errors appear in the backend container's own logs for the Loki sender

**Dependencies:** None

**Files:** `backend/src/main/resources/logback-spring.xml` (modify)

**Estimated scope:** XS (1 file, ~7 lines added)

---

### Checkpoint: Phase 1–2 complete
- [ ] `ci.yml` and `logback-spring.xml` changes reviewed and committed
- [ ] Backend still builds and local dev stack (`docker-compose.yml`) still works unchanged

---

### Phase 3 — Deploy artifacts (repo files; no cloud account needed yet)

#### Task 3: Write `deploy/alloy/config.alloy`

**Description:** Metrics-only Alloy config: scrape `backend:8080/actuator/prometheus`, remote-write to Grafana Cloud Mimir. Credentials via `sys.env()`, no hardcoded values.

**Corrected during planning review:** the original draft used `env(...)`, which is Grafana Agent Flow's older syntax. Verified against Grafana's current Alloy docs (`prometheus.remote_write` reference page) — the function is `sys.env(...)`. Using `env()` would have shipped a config that fails to parse or evaluate on first deploy.

**Acceptance criteria:**
- [ ] `prometheus.scrape` targets `backend:8080`, path `/actuator/prometheus`
- [ ] `prometheus.remote_write` uses `sys.env("GC_PROM_URL")` / `sys.env("GC_PROM_USER")` / `sys.env("GC_API_KEY")`
- [ ] No `otelcol.*` or `loki.*` components — traces/logs bypass Alloy entirely

**Verification:**
- [ ] Manual: `docker run --rm -v $(pwd)/deploy/alloy/config.alloy:/etc/alloy/config.alloy grafana/alloy:latest validate /etc/alloy/config.alloy` (or equivalent Alloy config-check command) passes

**Dependencies:** None (file can be drafted before the Grafana Cloud account exists; real values fill in at deploy time)

**Files:** `deploy/alloy/config.alloy` (new)

**Estimated scope:** S (1 file)

---

#### Task 4: Write `deploy/docker-compose.prod.yml`

**Description:** Production stack pulling GHCR images: postgres, redis, minio, backend (prod profile, OTLP/Loki/Mimir env vars), frontend, alloy, caddy.

**Corrected during implementation review:** a line-by-line diff of every env var the local `docker-compose.yml` backend service receives against this file surfaced three real gaps, not just naming differences:
- **`MINIO_URL` does nothing.** `MinioConfig.java`/`MinioStorageAdapter.java` read `app.minio.endpoint` (base `application.yml`, env var `MINIO_ENDPOINT`). `application-prod.yml`'s `app.storage.minio.url` is a dead, unused property path — a pre-existing bug in that file, not introduced here. Fixed to `MINIO_ENDPOINT`.
- **`FRONTEND_URL` was missing.** Used in `SmtpEmailAdapter` (password-reset email link) and `OAuth2SuccessHandler` (post-login redirect) via `app.frontend-url` in base `application.yml`. Without it, both silently point at `localhost:5173` in production.
- **SMTP vars were missing.** `spring.mail.*` in base `application.yml` applies to every profile; without `SMTP_HOST`/`PORT`/`USER`/`PASS`/`AUTH`/`STARTTLS`, password-reset email sending fails.

Ruled out as non-gaps: `JWT_SECRET` (passed in local compose, zero code references — dead config) and `APP_BASE_URL` (in the root `.env.example`, zero code references).

**Acceptance criteria:**
- [ ] No `build:` keys — only `image: ghcr.io/minhdau156/...` references
- [ ] No `loki`/`tempo`/`prometheus`/`grafana` services
- [ ] `backend` environment includes `MINIO_ENDPOINT` (not `MINIO_URL`), `FRONTEND_URL`, `GOOGLE_CLIENT_ID`/`SECRET`, `SMTP_*`, `OTEL_ENDPOINT`, `MANAGEMENT_OTLP_TRACING_HEADERS_AUTHORIZATION`, `LOKI_URL`, `LOKI_USER`, `LOKI_API_KEY`
- [ ] `alloy` service mounts `deploy/alloy/config.alloy` read-only and only receives `GC_PROM_*`/`GC_API_KEY` env vars

**Verification:**
- [ ] `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.example config` parses without error (using placeholder values)
- [ ] Manual diff review against `TASK-10.54-production-deployment-ec2-grafana-cloud.md` step 5

**Dependencies:** Task 3 (references the same Alloy config path)

**Files:** `deploy/docker-compose.prod.yml` (new)

**Estimated scope:** M (1 file, many services)

---

#### Task 5: Write `deploy/Caddyfile`

**Description:** Route `/api/*` to `backend:8080`, everything else to `frontend:80`, with automatic HTTPS.

**Acceptance criteria:**
- [ ] `${DOMAIN}` block with `handle /api/*` → `backend:8080` and default `handle` → `frontend:80`

**Verification:**
- [ ] Manual: `docker run --rm -v $(pwd)/deploy/Caddyfile:/etc/caddy/Caddyfile caddy:2-alpine caddy validate --config /etc/caddy/Caddyfile` passes

**Dependencies:** None

**Files:** `deploy/Caddyfile` (new)

**Estimated scope:** XS (1 file)

---

#### Task 6: Write `deploy/.env.example`

**Description:** Template listing every variable `docker-compose.prod.yml` and `config.alloy` read, with placeholder values.

**Acceptance criteria:**
- [ ] Includes `DOMAIN`, `DATABASE_PASSWORD`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `JWT_RSA_PRIVATE_KEY`, `JWT_RSA_PUBLIC_KEY`, `GC_PROM_URL`, `GC_PROM_USER`, `GC_LOKI_URL`, `GC_LOKI_USER`, `GC_TEMPO_OTLP_ENDPOINT`, `GC_API_KEY`, `GC_BASIC_AUTH_B64`
- [ ] No real secrets committed

**Verification:**
- [ ] Manual: every `${VAR}` referenced in Task 4/3's files has a corresponding line here

**Dependencies:** Tasks 3, 4

**Files:** `deploy/.env.example` (new)

**Estimated scope:** XS (1 file)

---

### Checkpoint: Phase 3 complete
- [ ] `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.example config` validates
- [ ] All four `deploy/` files committed together, reviewed as one diff

---

### Phase 4 — Cloud provisioning (manual, human-executed)

#### Task 7: Create the Grafana Cloud connection

**Description:** Sign up for Grafana Cloud free tier; add a metrics/Prometheus connection; record the Mimir remote-write URL + username, Loki push URL + username, Tempo OTLP endpoint, and one API key.

**Acceptance criteria:**
- [ ] All four values recorded somewhere safe (password manager / local `.env`, not committed)

**Verification:** Manual — values exist and are reachable from the Grafana Cloud portal's connection page

**Dependencies:** None

**Files:** None (cloud console)

**Estimated scope:** Manual

---

#### Task 8: Launch the EC2 instance

**Description:** Launch `t4g.small`, Ubuntu arm64 AMI, security group (22 restricted to your IP, 80/443 open), Elastic IP, Docker installed.

**Acceptance criteria:**
- [ ] Instance reachable via SSH
- [ ] `docker --version` and `docker compose version` succeed on the instance
- [ ] Elastic IP associated (survives a stop/start)

**Verification:** Manual — SSH in, run the version commands

**Dependencies:** None (can run in parallel with Task 7)

**Files:** None (AWS console)

**Estimated scope:** Manual

---

#### Task 9: Point DNS at the Elastic IP

**Acceptance criteria:**
- [ ] Domain's A record resolves to the Elastic IP (`dig +short ${DOMAIN}`)

**Dependencies:** Task 8

**Files:** None (DNS provider)

**Estimated scope:** Manual

---

### Checkpoint: Phase 4 complete
- [ ] Grafana Cloud credentials in hand
- [ ] EC2 instance reachable, Docker installed, DNS resolving correctly

---

### Phase 5 — Deploy and verify

#### Task 10: Deploy to EC2

**Description:** Clone the repo on the instance, fill `deploy/.env` with real secrets (including Task 7's Grafana Cloud values), bring up the stack.

**Acceptance criteria:**
- [ ] `docker compose -f docker-compose.prod.yml --env-file .env up -d` — all services report healthy/running
- [ ] `deploy/.env` is not committed (confirm `git status` on the instance shows it untracked or ignored)

**Verification:** Manual — `docker compose ps` shows all services up

**Dependencies:** Tasks 1–9

**Files:** None (deploy action; files already exist from Phase 3)

**Estimated scope:** Manual

---

#### Task 11: Verify end-to-end

**Acceptance criteria:**
- [ ] `curl -I https://${DOMAIN}/api/actuator/health` → `200`, valid TLS cert
- [ ] `curl -I https://${DOMAIN}/` → `200`
- [ ] Grafana Cloud Explore: `up{job="backend"}` in Mimir returns a value
- [ ] Grafana Cloud Explore: `{app="instagram"}` in Loki returns recent log lines
- [ ] Grafana Cloud Explore: Tempo search for service `instagram` returns recent traces

**Verification:** Manual, exactly the commands/queries above

**Dependencies:** Task 10

**Files:** None

**Estimated scope:** Manual

---

#### Task 12: Write `docs/infra/deployment.md`

**Description:** Runbook: deploy steps, update steps (`docker compose pull && up -d`), rollback (pin `sha-<commit>` tag), where to view dashboards, example Loki/Tempo queries. Written last so it reflects the real values/gotchas hit during Tasks 8–11.

**Acceptance criteria:**
- [ ] Follows the structure of `docs/infra/tls-setup.md`
- [ ] Covers deploy, update, rollback, and "where do I look when something breaks"

**Verification:** Manual review — a teammate unfamiliar with the setup could follow it

**Dependencies:** Task 11

**Files:** `docs/infra/deployment.md` (new)

**Estimated scope:** S (1 file)

---

### Checkpoint: Complete
- [ ] App live at `https://${DOMAIN}`
- [ ] Metrics, logs, and traces all visible in Grafana Cloud
- [ ] Runbook written and reviewed

## Risks and Mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| `loki-logback-appender` 1.5.2's `<auth>` tag names don't match what's assumed in Task 2 | Medium — logs silently stop shipping (appender swallows HTTP errors by default) | Verify against the installed version's docs during Task 2; check appender startup logs for config parse errors before deploying |
| `management.otlp.tracing.headers.Authorization` isn't the exact Spring Boot 3.3.4 property path | Medium — traces silently fail to authenticate | Verify during Task 11's trace check; fallback is adding an explicit `management.otlp.tracing.headers.authorization` line to `application-prod.yml` if env-var relaxed binding doesn't work |
| `t4g.small`'s 2 GB RAM is still tight once Postgres+Redis+MinIO+backend+frontend+Caddy+Alloy all run together | Low — Alloy here is metrics-only, far lighter than the full Loki/Tempo/Prometheus/Grafana stack it replaces | Monitor with `docker stats` after Task 10; step up to `t4g.medium` if needed |
| Multi-arch build (Task 1) increases CI build time | Low-Medium | **Correction (verified against `ci.yml`, not assumed):** there is no Docker layer cache (`cache-from`/`cache-to`) in `docker-publish` today — only Maven/npm dependency caching in the test jobs. QEMU-emulated `arm64` builds will add real wall-clock time with nothing offsetting it. Accept for now; revisit with `cache-to: type=gha` if CI time becomes a problem |

## Open Questions

None — the trace/log architecture questions were resolved by reading `application.yml` and `logback-spring.xml` directly rather than trusting the original task doc's assumptions.
