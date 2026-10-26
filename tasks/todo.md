# TODO: TASK-10.54 — Production Deployment (AWS EC2 + Grafana Cloud)

See `tasks/plan.md` for full detail, especially **Architecture Decisions** — the task doc originally assumed Zipkin tracing and Docker-log-tailing; the actual code already does OTLP tracing and direct-to-Loki logging, so Alloy is metrics-only below.

## Phase 1 — CI

### Task 1: Multi-arch (`arm64`) image builds

CI currently builds images for x86 only. The EC2 instance (`t4g.small`) runs ARM (Graviton) — an x86-only image either fails to run there or falls back to slow QEMU emulation at runtime. This task makes GHCR publish both architectures under one tag so Docker on the ARM box pulls the native `arm64` variant automatically.

- [ ] Add `docker/setup-qemu-action@v3` before the Buildx step in `docker-publish`
- [ ] Add `platforms: linux/amd64,linux/arm64` to both `docker/build-push-action@v6` steps
- [ ] Leave `backend-ci`, `frontend-ci`, and the `VITE_API_URL` build-arg untouched
- Files: `.github/workflows/ci.yml`
- Dependencies: None

## Phase 2 — Backend (minimal additive change)

### Task 2: Basic Auth + Grafana Cloud limits on the production Loki appender

The backend already pushes logs straight to Loki with no agent involved — but with no credentials. Grafana Cloud's Loki rejects unauthenticated pushes (401), so this adds a Basic Auth block. Verified against loki4j's own Grafana Cloud guide: it also requires a longer timeout and caps batches at 64 KB (below loki4j's default), so both get added too — otherwise logs will intermittently and silently fail to ship once volume grows. All of it scoped to the `!local` profile only, with empty-default env vars, so local dev keeps working exactly as today.

- [ ] Add `<auth><username>${LOKI_USER:-}</username><password>${LOKI_API_KEY:-}</password></auth>` and `<requestTimeoutMs>15000</requestTimeoutMs>` inside the `!local` `LOKI` appender's `<http>` block
- [ ] Add `<batch><maxBytes>65536</maxBytes></batch>` to the same appender
- [ ] Leave the `local` profile's `LOKI` appender byte-for-byte unchanged
- Files: `backend/src/main/resources/logback-spring.xml`
- Dependencies: None

## Checkpoint: Phase 1–2 complete

Confirm nothing that works today broke, before building on top of it.

- [ ] `mvn -q -DskipTests package` builds
- [ ] Local `docker-compose.yml` stack still logs to local Loki unauthenticated, unchanged
- [ ] `git diff` shows only the intended additive lines in each file

## Phase 3 — Deploy artifacts

No AWS or Grafana Cloud account needed yet — these four files use placeholders for credentials filled in later at Task 10.

### Task 3: `deploy/alloy/config.alloy` (metrics only)

Grafana Alloy's only job here: fetch (`scrape`) the backend's `/actuator/prometheus` endpoint and push (`remote_write`) it to Grafana Cloud Mimir. Needed because Micrometer's Prometheus registry is pull-only but Grafana Cloud needs data pushed to it — Alloy bridges that gap. It touches nothing else; traces and logs go directly from the backend (Task 2 and the env vars in Task 4), with no relay. Credentials read via `sys.env(...)` — verified against current Alloy docs; `env(...)` is the older Grafana Agent Flow syntax and would fail here.

- [ ] `prometheus.scrape` of `backend:8080/actuator/prometheus`
- [ ] `prometheus.remote_write` to `sys.env("GC_PROM_URL")` with `sys.env("GC_PROM_USER")`/`sys.env("GC_API_KEY")`
- [ ] No `otelcol.*` or `loki.*` components
- Files: `deploy/alloy/config.alloy`
- Dependencies: None

### Task 4: `deploy/docker-compose.prod.yml`

The actual production stack: which containers run and how they connect. Unlike local `docker-compose.yml`, this pulls pre-built GHCR images instead of building from source, and has no Loki/Tempo/Prometheus/Grafana containers — those responsibilities moved to Grafana Cloud. This is the file that actually runs on the EC2 box.

**Found during review** by diffing every env var the local compose file's backend service gets against this one: `MINIO_URL` doesn't actually do anything (the code reads `app.minio.endpoint` / env var `MINIO_ENDPOINT` — `application-prod.yml`'s `app.storage.minio.url` is dead, pre-existing, unused config), and `FRONTEND_URL` + the `SMTP_*` vars were missing entirely (used for password-reset email links, OAuth2 login redirects, and sending the email itself). All fixed.

- [ ] Services: postgres, redis, minio, backend, frontend, alloy, caddy — no `build:` keys, no loki/tempo/prometheus/grafana
- [ ] Backend env includes `MINIO_ENDPOINT` (not `MINIO_URL`), `FRONTEND_URL`, `GOOGLE_CLIENT_ID`/`SECRET`, `SMTP_*`, `OTEL_ENDPOINT`, `MANAGEMENT_OTLP_TRACING_HEADERS_AUTHORIZATION`, `LOKI_URL`, `LOKI_USER`, `LOKI_API_KEY`
- [ ] Alloy service mounts `config.alloy` read-only
- Files: `deploy/docker-compose.prod.yml`
- Dependencies: Task 3

### Task 5: `deploy/Caddyfile`

Caddy is the reverse proxy in front of everything — it gets HTTPS certificates automatically from Let's Encrypt, no manual cert work. This one-page config routes `/api/*` to the backend and everything else to the frontend.

- [ ] `/api/*` → `backend:8080`, else → `frontend:80`
- Files: `deploy/Caddyfile`
- Dependencies: None

### Task 6: `deploy/.env.example`

A template listing every variable the compose file and Alloy config expect (domain, DB password, MinIO keys, JWT keys, Grafana Cloud URLs/credentials), same role as the project's root `.env.example` but for this deploy. Real secrets never get committed — only this placeholder template does.

- [ ] Every `${VAR}` used by Tasks 3–4 has a placeholder line
- Files: `deploy/.env.example`
- Dependencies: Tasks 3, 4

## Checkpoint: Phase 3 complete

Catch config typos for free before ever touching a real server.

- [ ] `docker compose -f deploy/docker-compose.prod.yml --env-file deploy/.env.example config` validates
- [ ] All four `deploy/` files reviewed together

## Phase 4 — Cloud provisioning (manual, no code)

### Task 7: Grafana Cloud connection

Sign up for the free tier and add a metrics connection in the Grafana Cloud UI. This hands over four things needed later: the Mimir (metrics) push URL + username, the Loki (logs) push URL + username, the Tempo (traces) endpoint, and one API key that acts as the password for all three.

- [ ] Sign up free tier, add metrics connection
- [ ] Record Mimir remote-write URL+user, Loki push URL+user, Tempo OTLP endpoint, API key
- Dependencies: None

### Task 8: EC2 instance

The actual server. Launch it, open only the necessary ports (SSH restricted to your own IP, 80/443 open to everyone), attach a fixed Elastic IP so the address survives a restart, and install Docker.

- [ ] Launch `t4g.small`, Ubuntu arm64 AMI
- [ ] Security group: 22 (restricted), 80, 443
- [ ] Elastic IP associated
- [ ] Docker + Compose installed, verified via SSH
- Dependencies: None

### Task 9: DNS

Point the domain at that fixed IP so `https://yourdomain.com` actually resolves to the server.

- [ ] A record → Elastic IP, confirmed via `dig +short ${DOMAIN}`
- Dependencies: Task 8

## Checkpoint: Phase 4 complete

Confirm every credential and the server itself are ready before attempting a deploy.

- [ ] Grafana Cloud credentials in hand
- [ ] EC2 reachable, Docker installed, DNS resolving

## Phase 5 — Deploy and verify

### Task 10: Deploy to EC2

Copy the real `.env` onto the server (filled in with actual secrets, including Task 7's Grafana Cloud values) and bring the stack up. This is the moment the app actually goes live.

- [ ] `cp .env.example .env`, fill real values (incl. Grafana Cloud creds)
- [ ] `docker compose -f docker-compose.prod.yml --env-file .env up -d` — all services healthy
- [ ] `.env` not committed
- Dependencies: Tasks 1–9

### Task 11: Verify end-to-end

Don't just trust the containers started — confirm the app actually works over HTTPS, and specifically confirm metrics, logs, and traces are genuinely arriving in Grafana Cloud, not just that nothing crashed.

- [ ] `curl -I https://${DOMAIN}/api/actuator/health` → 200, valid cert
- [ ] `curl -I https://${DOMAIN}/` → 200
- [ ] Grafana Cloud: `up{job="backend"}` returns a value (Mimir)
- [ ] Grafana Cloud: `{app="instagram"}` returns log lines (Loki)
- [ ] Grafana Cloud: Tempo search for service `instagram` returns traces
- Dependencies: Task 10

### Task 12: `docs/infra/deployment.md`

Written last on purpose, so it reflects the real gotchas hit along the way. Documents how to deploy, update, roll back a bad release, and where to look in Grafana Cloud when something breaks — so this isn't starting from zero next time.

- [ ] Deploy / update / rollback / "where to look when it breaks", following `docs/infra/tls-setup.md`'s structure
- Files: `docs/infra/deployment.md`
- Dependencies: Task 11

## Checkpoint: Complete

- [ ] App live at `https://${DOMAIN}`
- [ ] Metrics, logs, traces all visible in Grafana Cloud
- [ ] Runbook written
