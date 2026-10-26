# TASK-10.54 — Production Deployment (AWS EC2 + Grafana Cloud)

## Overview

Deploy the app to a single AWS EC2 instance (backend, frontend, Postgres, Redis, MinIO, Caddy) and send all observability data — metrics, logs, traces — to **Grafana Cloud's free tier** instead of self-hosting Prometheus/Loki/Tempo/Grafana. The backend already exports traces via OTLP and pushes logs directly to Loki ([TASK-10.28](TASK-10.28-distributed-tracing.md), [TASK-10.30](TASK-10.30-loki-log-aggregation.md)) — production only needs to repoint those at Grafana Cloud's endpoints. Metrics are the one gap: Micrometer's Prometheus registry is pull-only, so a single lightweight **Grafana Alloy** container is added purely to scrape `/actuator/prometheus` and push it to Grafana Cloud.

---

## Level

**Core** — Depends on [TASK-10.44](TASK-10.44-dockerfile-backend.md), [TASK-10.45](TASK-10.45-dockerfile-frontend.md), [TASK-10.46](TASK-10.46-docker-compose.md), [TASK-10.47](TASK-10.47-cicd-docker-build-push.md), [TASK-10.27](TASK-10.27-actuator-micrometer.md), [TASK-10.28](TASK-10.28-distributed-tracing.md) (OTLP tracing), and [TASK-10.30](TASK-10.30-loki-log-aggregation.md) (Loki logging). Pairs with [TASK-10.20](TASK-10.20-https-tls.md).

---

## Architecture

```
AWS EC2 (t4g.small, ARM/Graviton, 2 vCPU / 2 GB)
├── postgres, redis, minio   (data layer)
├── backend  ──── OTLP traces ───────────────┐
│          └───── Loki push (logs) ──────────┤
├── frontend                                 │
├── alloy  ── scrapes /actuator/prometheus   │
│         └── remote_write (metrics) ────────┤
└── caddy  (reverse proxy + auto TLS)        │
                                              ▼
                                   Grafana Cloud (free tier)
                                   ├── Mimir  (metrics, via Alloy remote_write)
                                   ├── Loki   (logs, pushed directly by the app)
                                   ├── Tempo  (traces, pushed directly by the app via OTLP)
                                   └── Grafana (dashboards — hosted, nothing to run yourself)
```

Alloy's only job is metrics. Traces and logs go straight from the backend container to Grafana Cloud — no relay needed, because the app already speaks OTLP and Loki's push API natively.

---

## Why This Combination

- **EC2 `t4g.small` is ~$12–14/mo** (not free-tier eligible — only `.micro` sizes are) and gives 2 GB RAM. Removing Loki/Tempo/Prometheus/Grafana (the heaviest part of the local stack) and replacing them with one lightweight Alloy container makes that comfortable.
- **Grafana Cloud's free tier costs $0** and avoids the anti-pattern of self-hosted observability: if the instance running your app goes down, you lose the dashboards that would tell you why.
- **Minimal code surface:** traces and logs reuse existing config paths (`OTEL_ENDPOINT`, `LOKI_URL`) almost unchanged — only the Loki appender needs a small additive auth block, since Grafana Cloud requires Basic Auth and the current appender sends none.
- **Graviton (ARM) is cheaper per vCPU than x86** (`t4g.small` < `t3.small`), but CI currently builds x86-only images — this task adds multi-arch builds.
- Self-hosted Kubernetes (Phase 14) stays deferred — this gets the app live now without redoing the deploy twice.

---

## Prerequisites

- An AWS account and a free Grafana Cloud account (https://grafana.com/products/cloud/) — the free plan is permanent (no forced upgrade), with a 14-day retention window on the free usage allowance.
- A domain name with an A record you can point at the instance's IP.
- Values for every variable in `.env.example` (JWT keys, OAuth2, MinIO credentials, DB password, SMTP).
- **Concept gloss:**
  - **Grafana Alloy** — Grafana Labs' unified telemetry agent. Here it does one thing: `prometheus.scrape` the backend's metrics endpoint, `prometheus.remote_write` them to Grafana Cloud Mimir.
  - **OTLP (OpenTelemetry Protocol)** — the wire format the backend already uses to export traces (`management.otlp.tracing.endpoint`, Spring Boot 3.3's built-in OTLP exporter). Grafana Cloud's Tempo accepts OTLP directly, so production just points the existing `OTEL_ENDPOINT` env var at Grafana Cloud instead of a local Tempo container.
  - **`management.otlp.tracing.headers`** — a Spring Boot property (Map<String,String>) for adding custom headers, such as `Authorization`, to every trace export call. Settable purely via environment variable (`MANAGEMENT_OTLP_TRACING_HEADERS_AUTHORIZATION`) — no code change. Verify the exact property name against the Spring Boot 3.3.4 reference docs during implementation.
  - **loki4j Basic Auth** — `loki-logback-appender` (already a dependency, v1.5.2) supports an `<auth><username>/<password></auth>` block nested under `<http>`. Verify the exact tag names against the installed version's README during implementation.
  - **Graviton / `arm64`** — AWS's ARM-based processor family. Docker images must be built for `linux/arm64`, not just `linux/amd64`, to run natively.

---

## Files to Create / Modify

```
.github/workflows/ci.yml                            (modify — multi-arch image builds)
backend/src/main/resources/logback-spring.xml        (modify — add optional Basic Auth to the !local Loki appender)
deploy/docker-compose.prod.yml                       (new — production stack: postgres, redis, minio, backend, frontend, alloy, caddy)
deploy/Caddyfile                                     (new)
deploy/alloy/config.alloy                            (new — metrics scrape + remote_write to Grafana Cloud)
deploy/.env.example                                  (new)
docs/infra/deployment.md                             (new — runbook)
```

---

## Step-by-Step

### 1. Add multi-arch builds to CI

Open `.github/workflows/ci.yml`, in the `docker-publish` job. Add a QEMU setup step before the existing Buildx step, and add `platforms` to both `docker/build-push-action` calls:

```yaml
- name: Set up QEMU
  uses: docker/setup-qemu-action@v3

- name: Set up Docker Buildx
  uses: docker/setup-buildx-action@v3
```

```yaml
- name: Build and push backend image
  uses: docker/build-push-action@v6
  with:
    context: ./backend
    file: ./backend/Dockerfile
    platforms: linux/amd64,linux/arm64
    push: true
    tags: ${{ steps.meta-backend.outputs.tags }}
```

Add the same `platforms:` line to the frontend build step. Leave the frontend's `build-args: VITE_API_URL=http://localhost:8080` exactly as-is — that's an intentional existing setting for local-only image use, not something this task changes. No Dockerfile changes are needed; `eclipse-temurin`, `node`, and `nginx` base images already publish `arm64` variants.

### 2. Add Basic Auth to the production Loki appender

Open `backend/src/main/resources/logback-spring.xml`. In the `<springProfile name="!local">` block, add an `<auth>` element inside the existing `LOKI` appender's `<http>` block:

```xml
<appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
  <http>
    <url>${LOKI_URL:-http://localhost:3100}/loki/api/v1/push</url>
    <auth>
      <username>${LOKI_USER:-}</username>
      <password>${LOKI_API_KEY:-}</password>
    </auth>
    <requestTimeoutMs>15000</requestTimeoutMs>
  </http>
  <batch>
    <maxBytes>65536</maxBytes>
  </batch>
  <!-- ... rest unchanged ... -->
</appender>
```

Empty defaults mean auth is inert unless `LOKI_USER`/`LOKI_API_KEY` are set — any other non-local deployment (staging, CI) keeps working unauthenticated. The longer timeout and 64 KB batch cap match [loki4j's own Grafana Cloud guide](https://loki4j.github.io/loki-logback-appender/docs/grafanacloud) — Grafana Cloud rejects batches larger than 65536 bytes, which loki4j's default exceeds, so without this cap, logs will intermittently and silently fail to ship once volume grows. **Do not touch the `local` profile's appender** — local dev keeps pushing to the self-hosted Loki container with no auth, no timeout change, and no batch cap.

### 3. Create a Grafana Cloud connection

In the Grafana Cloud portal, add a Prometheus/metrics connection to get:
- A Mimir `remote_write` URL + instance ID (username)
- A Loki push URL + instance ID (username)
- A Tempo OTLP endpoint
- One API key, used as the password/bearer token for all three

### 4. Create `deploy/alloy/config.alloy`

Metrics only — traces and logs bypass Alloy entirely:

```river
prometheus.scrape "backend" {
  targets      = [{__address__ = "backend:8080"}]
  metrics_path = "/actuator/prometheus"
  forward_to   = [prometheus.remote_write.grafana_cloud.receiver]
}

prometheus.remote_write "grafana_cloud" {
  endpoint {
    url = sys.env("GC_PROM_URL")
    basic_auth {
      username = sys.env("GC_PROM_USER")
      password = sys.env("GC_API_KEY")
    }
  }
}
```

> **Note:** the function is `sys.env(...)`, not `env(...)` — that was the older Grafana Agent Flow syntax. Current Alloy uses `sys.env`, confirmed against Grafana's own docs.

### 5. Create `deploy/docker-compose.prod.yml`

```yaml
services:
  postgres:
    image: postgres:15
    restart: unless-stopped
    environment:
      POSTGRES_DB: instagram
      POSTGRES_USER: instagram
      POSTGRES_PASSWORD: ${DATABASE_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    restart: unless-stopped

  minio:
    image: minio/minio
    restart: unless-stopped
    environment:
      MINIO_ROOT_USER: ${MINIO_ACCESS_KEY}
      MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY}
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data

  backend:
    image: ghcr.io/minhdau156/instagram-backend:latest
    restart: unless-stopped
    environment:
      SPRING_PROFILES_ACTIVE: prod
      DATABASE_URL: jdbc:postgresql://postgres:5432/instagram
      DATABASE_USERNAME: instagram
      DATABASE_PASSWORD: ${DATABASE_PASSWORD}
      REDIS_URL: redis://redis:6379
      # The code reads app.minio.endpoint (base application.yml, env var
      # MINIO_ENDPOINT) — application-prod.yml's app.storage.minio.url is a
      # dead/unused property path. Use MINIO_ENDPOINT, not MINIO_URL.
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY}
      MINIO_SECRET_KEY: ${MINIO_SECRET_KEY}
      MINIO_BUCKET: instagram-media
      CORS_ALLOWED_ORIGINS: https://${DOMAIN}
      FRONTEND_URL: https://${DOMAIN}
      JWT_RSA_PRIVATE_KEY: ${JWT_RSA_PRIVATE_KEY}
      JWT_RSA_PUBLIC_KEY: ${JWT_RSA_PUBLIC_KEY}
      GOOGLE_CLIENT_ID: ${GOOGLE_CLIENT_ID}
      GOOGLE_CLIENT_SECRET: ${GOOGLE_CLIENT_SECRET}
      SMTP_HOST: ${SMTP_HOST}
      SMTP_PORT: ${SMTP_PORT}
      SMTP_USER: ${SMTP_USER}
      SMTP_PASS: ${SMTP_PASS}
      SMTP_AUTH: ${SMTP_AUTH}
      SMTP_STARTTLS: ${SMTP_STARTTLS}
      # Traces: straight to Grafana Cloud Tempo, no relay
      OTEL_ENDPOINT: ${GC_TEMPO_OTLP_ENDPOINT}
      MANAGEMENT_OTLP_TRACING_HEADERS_AUTHORIZATION: "Basic ${GC_BASIC_AUTH_B64}"
      # Logs: straight to Grafana Cloud Loki, no relay
      LOKI_URL: ${GC_LOKI_URL}
      LOKI_USER: ${GC_LOKI_USER}
      LOKI_API_KEY: ${GC_API_KEY}
    depends_on:
      - postgres
      - redis
      - minio

  frontend:
    image: ghcr.io/minhdau156/instagram-frontend:latest
    restart: unless-stopped

  alloy:
    image: grafana/alloy:latest
    restart: unless-stopped
    volumes:
      - ./alloy/config.alloy:/etc/alloy/config.alloy:ro
    environment:
      GC_PROM_URL: ${GC_PROM_URL}
      GC_PROM_USER: ${GC_PROM_USER}
      GC_API_KEY: ${GC_API_KEY}
    command: run /etc/alloy/config.alloy --server.http.listen-addr=0.0.0.0:12345
    depends_on:
      - backend

  caddy:
    image: caddy:2-alpine
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./Caddyfile:/etc/caddy/Caddyfile:ro
      - caddy_data:/data
    depends_on:
      - backend
      - frontend

volumes:
  postgres_data:
  minio_data:
  caddy_data:
```

`GC_BASIC_AUTH_B64` is `base64("<Grafana Cloud instance ID>:<API key>")`, computed once and stored in `.env` (not committed).

### 6. Create `deploy/Caddyfile`

```
${DOMAIN} {
    handle /api/* {
        reverse_proxy backend:8080
    }
    handle {
        reverse_proxy frontend:80
    }
}
```

### 7. Create `deploy/.env.example`

List every variable the compose file reads (`DOMAIN`, `DATABASE_PASSWORD`, `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY`, `JWT_RSA_PRIVATE_KEY`, `JWT_RSA_PUBLIC_KEY`, `GC_PROM_URL`, `GC_PROM_USER`, `GC_LOKI_URL`, `GC_LOKI_USER`, `GC_TEMPO_OTLP_ENDPOINT`, `GC_API_KEY`, `GC_BASIC_AUTH_B64`) with placeholders, mirroring the root `.env.example` style.

### 8. Launch the EC2 instance

- AMI: Ubuntu 22.04/24.04, **arm64** architecture.
- Instance type: `t4g.small` (2 vCPU / 2 GB).
- Security group: allow `22` (SSH, restrict to your IP), `80`, `443` from `0.0.0.0/0`.
- Allocate and associate an **Elastic IP** — a plain public IP changes if the instance stops/restarts, which would break your DNS A record.
- Point your domain's A record at the Elastic IP.

Install Docker:

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
```

### 9. Deploy

```bash
git clone <repo-url> && cd Instagram-Social-/deploy
cp .env.example .env   # fill in real values
docker compose -f docker-compose.prod.yml --env-file .env up -d
```

### 10. Write `docs/infra/deployment.md`

Document: deploy steps, update steps (`docker compose pull && docker compose up -d`), where to view dashboards (Grafana Cloud URL), and how to query logs/traces there. Follow the structure of `docs/infra/tls-setup.md`.

---

## Checklist

- [ ] `ci.yml` builds and pushes `linux/amd64,linux/arm64` images for both backend and frontend
- [ ] `logback-spring.xml`'s `!local` Loki appender has an additive `<auth>` block; `local` profile is untouched
- [ ] EC2 instance (`t4g.small`, arm64 AMI) running with Elastic IP and correct security group
- [ ] `deploy/docker-compose.prod.yml` has no `loki`/`tempo`/`prometheus`/`grafana` services — only `alloy` for metrics
- [ ] `alloy/config.alloy` scrapes `/actuator/prometheus` and remote-writes to Grafana Cloud Mimir — nothing else
- [ ] Backend's `OTEL_ENDPOINT` and `LOKI_URL`/`LOKI_USER`/`LOKI_API_KEY` point at Grafana Cloud, not a local container
- [ ] Metrics, logs, and traces all visible in Grafana Cloud's hosted Grafana
- [ ] App reachable at `https://${DOMAIN}` with a valid certificate

---

## How to Verify

```bash
curl -I https://yourdomain.com/api/actuator/health
# Expected: HTTP/2 200, valid TLS cert

docker compose -f docker-compose.prod.yml logs alloy --tail=50
# Expected: no scrape/remote_write errors

docker compose -f docker-compose.prod.yml logs backend --tail=50 | grep -i "loki\|otlp"
# Expected: no 401/connection-refused errors from the Loki or OTLP exporters
```

In Grafana Cloud's Explore view:
- Query Mimir for `up{job="backend"}` → should return a value.
- Query Loki for `{app="instagram"}` → should return recent log lines.
- Search Tempo for service name `instagram` → should return recent traces.

---

## Notes / Gotchas

**`application-prod.yml`'s MinIO config is dead code — don't trust it.** It defines `app.storage.minio.url/access-key/secret-key/bucket`, but `MinioConfig.java` and `MinioStorageAdapter.java` actually read `app.minio.endpoint/access-key/secret-key/bucket` (set in the base `application.yml`, active in every profile). The two property paths never connect. Set `MINIO_ENDPOINT` (not `MINIO_URL`) or file uploads will silently fail against an unreachable `localhost:9000` default.

**`FRONTEND_URL` is required, not optional.** It feeds both the password-reset email link (`SmtpEmailAdapter`) and the post-login redirect after Google OAuth2 (`OAuth2SuccessHandler`). Without it, both silently point at `localhost:5173` in production — no error, just a broken link/redirect a real user would hit.

**Google OAuth2 needs a Console update too, not just env vars.** Spring derives the redirect URI as `{baseUrl}/login/oauth2/code/google`. Add `https://${DOMAIN}/login/oauth2/code/google` to the OAuth app's Authorized Redirect URIs in Google Cloud Console, or login fails with `redirect_uri_mismatch`.

**EC2 security groups aren't the only firewall.** Ubuntu's own `ufw`/`iptables` can still block traffic even if the security group allows it.

**`t4g.small` is burstable (T-series).** It uses CPU credits; sustained heavy load will throttle performance once credits run out. Fine for low/moderate traffic.

**Don't commit `deploy/.env`.** Only `.env.example` goes in git — secrets (including the Grafana Cloud API key) stay on the instance only.

**Rollback:** pin `image:` to a specific `sha-<commit>` tag (already produced by [TASK-10.47](TASK-10.47-cicd-docker-build-push.md)) instead of `latest` for controlled releases.

**Related tasks:**
- [TASK-10.27 (Actuator & Micrometer)](TASK-10.27-actuator-micrometer.md) — source of the `/actuator/prometheus` endpoint Alloy scrapes.
- [TASK-10.28 (Distributed tracing)](TASK-10.28-distributed-tracing.md) — source of the OTLP export this task repoints at Grafana Cloud.
- [TASK-10.30 (Loki log aggregation)](TASK-10.30-loki-log-aggregation.md) — source of the direct-to-Loki logging this task repoints at Grafana Cloud.
- [TASK-10.46 (Full docker-compose.yml)](TASK-10.46-docker-compose.md) — local dev keeps the self-hosted Loki/Tempo/Prometheus/Grafana stack; only production routes through Grafana Cloud.
- `docs/tasks/phase-14-kubernetes-deployment.md` — the next step up if a single instance stops being enough.

---

## Learning Resources

### Concepts to learn
- **Remote write & the Prometheus scrape model** — https://prometheus.io/docs/practices/remote_write/
- **AWS Graviton (ARM) processors** — https://aws.amazon.com/ec2/graviton/

### Official docs (code reference)
- **Grafana Alloy** — https://grafana.com/docs/alloy/latest/
- **Grafana Cloud free tier** — https://grafana.com/pricing/
- **Docker multi-platform builds** — https://docs.docker.com/build/building/multi-platform/
