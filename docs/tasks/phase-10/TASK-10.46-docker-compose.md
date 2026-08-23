# TASK-10.46 — Full docker-compose.yml

## Overview

Update `docker-compose.yml` (repo root) to include the full application stack: the Spring Boot backend, the React/nginx frontend, PostgreSQL, MinIO, Redis, and Zipkin. Each service gets a `healthcheck` block so Docker knows when it is actually ready to accept traffic, and `depends_on` with `condition: service_healthy` enforces startup ordering — the backend will not start until Postgres and Redis are healthy, and the frontend will not start until the backend is healthy. The goal is a single `docker compose up` command that reliably brings the whole system online.

## Level

**Core** · Pairs with [TASK-10.44](TASK-10.44-dockerfile-backend.md) (backend Dockerfile), [TASK-10.45](TASK-10.45-dockerfile-frontend.md) (frontend Dockerfile), [TASK-10.42](TASK-10.42-smoke-test-script.md) (smoke test), and [TASK-10.47](TASK-10.47-cicd-docker-build-push.md) (CI build & push).

## Why

The existing `docker-compose.yml` only starts the three infrastructure services (Postgres, MinIO, Redis) — the backend and frontend still run outside Docker via `mvn spring-boot:run` and `npm run dev`. This is convenient for development iteration but means the stack that developers run locally is different from the stack that ships to production. Adding the application services to Compose gives every teammate a single reproducible command to bring up the full system, and healthchecks make the startup deterministic: rather than hoping services are ready, Docker will wait for them to pass their health probe before starting their dependents.

## Prerequisites

- Docker Desktop installed and running.
- `backend/Dockerfile` created ([TASK-10.44](TASK-10.44-dockerfile-backend.md)).
- `frontend/Dockerfile` and `frontend/nginx.conf` created ([TASK-10.45](TASK-10.45-dockerfile-frontend.md)).
- A `.env` file at the repo root (or exported environment variables) with the values required by the Compose file. See Step 1 for the required variables.
- **Concepts to skim:**
  - _`depends_on` with `condition: service_healthy`_ — makes Docker wait for a service's healthcheck to pass before starting the dependent. Without `condition:`, `depends_on` only waits for the container to start, not for the process inside to be ready. See [Docker Compose `depends_on`](https://docs.docker.com/compose/compose-file/05-services/#depends_on).
  - _`healthcheck`_ — a command Docker runs inside the container on an interval to decide if the service is `healthy`, `unhealthy`, or `starting`. See [Docker Compose `healthcheck`](https://docs.docker.com/compose/compose-file/05-services/#healthcheck).
  - _Named volumes_ — `volumes:` at the top level of the Compose file declares named volumes that persist data across `docker compose down` (but are removed by `docker compose down -v`).

## Files to Create / Modify

```
docker-compose.yml         (modify — replace existing content)
README.md                  (modify — add startup order and port mapping table)
.env.example               (new — document all required env vars)
```

## Step-by-Step

### 1. Create `.env.example` (commit this; keep `.env` gitignored)

Create `.env.example` at the repo root:

```dotenv
# ── PostgreSQL ───────────────────────────────────────────────────────────────
POSTGRES_DB=instagram
POSTGRES_USER=instagram
POSTGRES_PASSWORD=changeme

# ── MinIO ────────────────────────────────────────────────────────────────────
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin

# ── Redis ─────────────────────────────────────────────────────────────────────
# No auth required for local dev; set REDIS_PASSWORD in production.

# ── Backend ───────────────────────────────────────────────────────────────────
# Base64-encoded PKCS8 private key / X.509 public key (single line, no PEM headers).
# Generate a real pair for local dev — do not reuse the example values.
JWT_RSA_PRIVATE_KEY=change-this-to-a-base64-pkcs8-private-key
JWT_RSA_PUBLIC_KEY=change-this-to-a-base64-x509-public-key
SPRING_PROFILES_ACTIVE=local

# ── Frontend ──────────────────────────────────────────────────────────────────
# Vite bakes this value into the bundle at build time.
VITE_API_BASE_URL=http://localhost:8080
```

Copy this to `.env` and fill in real values for local development:

```powershell
Copy-Item .env.example .env
```

Bash:

```bash
cp .env.example .env
```

Make sure `.env` is in `.gitignore`:

```
.env
```

### 2. Replace `docker-compose.yml` with the full stack

Open `docker-compose.yml` at the repo root and replace its contents:

```yaml
# Full application stack — starts all services in dependency order.
# Run: docker compose up
# Stop: docker compose down
# Wipe volumes: docker compose down -v

services:

  # ── Infrastructure ────────────────────────────────────────────────────────

  postgres:
    image: postgres:15
    restart: unless-stopped
    ports:
      - "5432:5432"
    environment:
      POSTGRES_DB: ${POSTGRES_DB}
      POSTGRES_USER: ${POSTGRES_USER}
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${POSTGRES_USER} -d ${POSTGRES_DB}"]
      interval: 5s
      timeout: 5s
      retries: 10
      start_period: 10s

  redis:
    image: redis:7-alpine
    restart: unless-stopped
    ports:
      - "6379:6379"
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 5s
      retries: 5

  minio:
    image: minio/minio
    restart: unless-stopped
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: ${MINIO_ROOT_USER}
      MINIO_ROOT_PASSWORD: ${MINIO_ROOT_PASSWORD}
    command: server /data --console-address ":9001"
    volumes:
      - minio_data:/data
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9000/minio/health/live"]
      interval: 10s
      timeout: 5s
      retries: 5
      start_period: 10s

  zipkin:
    image: openzipkin/zipkin
    restart: unless-stopped
    ports:
      - "9411:9411"
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:9411/health"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ── Application ───────────────────────────────────────────────────────────

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    image: instagram-backend:local
    restart: unless-stopped
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB}
      SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PORT: 6379
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: ${MINIO_ROOT_USER}
      MINIO_SECRET_KEY: ${MINIO_ROOT_PASSWORD}
      MANAGEMENT_ZIPKIN_TRACING_ENDPOINT: http://zipkin:9411/api/v2/spans
      JWT_RSA_PRIVATE_KEY: ${JWT_RSA_PRIVATE_KEY}
      JWT_RSA_PUBLIC_KEY: ${JWT_RSA_PUBLIC_KEY}
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-local}
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 12
      start_period: 30s

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
      args:
        VITE_API_BASE_URL: ${VITE_API_BASE_URL:-http://localhost:8080}
    image: instagram-frontend:local
    restart: unless-stopped
    ports:
      - "3000:80"
    depends_on:
      backend:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost/"]
      interval: 10s
      timeout: 5s
      retries: 5

volumes:
  postgres_data:
  minio_data:
```

### 3. Add build arg support to the frontend Dockerfile

To allow `VITE_API_BASE_URL` to be injected at build time, update `frontend/Dockerfile` to accept it as a build argument. Add these two lines just before `RUN npm run build`:

```dockerfile
ARG VITE_API_BASE_URL=http://localhost:8080
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL
```

### 4. Start the full stack

From the repo root:

```powershell
docker compose up --build
```

The `--build` flag forces a rebuild of the `backend` and `frontend` images even if Docker has a cached version. After the first build, you can use `docker compose up` without `--build` for faster restarts.

Expected startup order (Docker enforces this via healthchecks):

```
1. postgres   — starts first, no dependencies
2. redis      — starts first, no dependencies
3. minio      — starts first, no dependencies
4. zipkin     — starts first, no dependencies
5. backend    — waits for postgres healthy + redis healthy
6. frontend   — waits for backend healthy
```

### 5. Confirm all services are healthy

In a second terminal (while `docker compose up` is running in the first):

```powershell
docker compose ps
```

Expected output — all services should show `healthy` in the STATUS column:

```
NAME                    STATUS
instagram-postgres-1    running (healthy)
instagram-redis-1       running (healthy)
instagram-minio-1       running (healthy)
instagram-zipkin-1      running (healthy)
instagram-backend-1     running (healthy)
instagram-frontend-1    running (healthy)
```

If any service shows `starting` give it another 30 seconds; if it shows `unhealthy` see the Notes / Gotchas section.

### 6. Run the smoke test

```powershell
bash scripts/smoke.sh
```

Passing result: `SMOKE TEST PASSED` (see [TASK-10.42](TASK-10.42-smoke-test-script.md)).

### 7. Update `README.md` with port mapping

Add a **Services** table to the repo root `README.md` so every teammate knows which port maps to which service:

```markdown
## Running the Full Stack

```bash
docker compose up --build
```

### Port Mapping

| Service | Host Port | URL |
|---------|-----------|-----|
| Frontend (nginx) | 3000 | http://localhost:3000 |
| Backend API | 8080 | http://localhost:8080 |
| Swagger UI | 8080 | http://localhost:8080/swagger-ui.html |
| PostgreSQL | 5432 | `psql -h localhost -U instagram` |
| MinIO API | 9000 | http://localhost:9000 |
| MinIO Console | 9001 | http://localhost:9001 |
| Redis | 6379 | `redis-cli -h localhost` |
| Zipkin UI | 9411 | http://localhost:9411 |

### Startup Order

Docker enforces this via healthchecks:
`postgres` + `redis` + `minio` + `zipkin` → `backend` → `frontend`
```

## Checklist

- [ ] Update `docker-compose.yml` to include all services:
  - [ ] `backend` (depends on `postgres`, `redis`)
  - [ ] `frontend` (depends on `backend`)
  - [ ] `postgres`, `minio`, `redis`, `zipkin`
- [ ] Add `healthcheck` blocks for all services
- [ ] Document startup order and port mapping in `README.md`

## How to Verify

1. Start the full stack:

   ```powershell
   docker compose up --build -d
   ```

2. Wait ~90 seconds for all healthchecks to pass, then:

   ```powershell
   docker compose ps
   ```

   Passing result: every service shows `running (healthy)`.

3. Open `http://localhost:3000` in a browser. The React app loads and the login page is visible.

4. Navigate directly to `http://localhost:3000/search` in the browser (or paste the URL after a page load). The app should load, not show a browser 404.

5. Run the smoke script:

   ```powershell
   bash scripts/smoke.sh
   ```

   Passing result: `SMOKE TEST PASSED`.

6. Confirm the backend is talking to Postgres (check the Spring Boot startup logs):

   ```powershell
   docker compose logs backend | Select-String "HikariPool\|Flyway\|Started"
   ```

   Passing result: Flyway migration output followed by `Started SocialMediaApplication`.

## Notes / Gotchas

- **`service_healthy` requires a healthcheck to be defined.** If a `depends_on` service has no `healthcheck` block, Docker silently ignores `condition: service_healthy` and uses `condition: service_started` instead, which may not be ready. Every service in this Compose file has an explicit `healthcheck`.

- **Backend healthcheck uses `curl`.** The `eclipse-temurin:21-jre-alpine` base image does not include `curl` by default. Add it to the backend Dockerfile's `run` stage:

  ```dockerfile
  RUN apk add --no-cache curl
  ```

  Without this, the healthcheck command `curl -f http://localhost:8080/actuator/health` will fail with `sh: curl: not found` and the backend will be stuck in the `starting` state.

- **`JWT_RSA_PRIVATE_KEY` / `JWT_RSA_PUBLIC_KEY` have no default value.** `application.yml` binds `app.jwt.rsa-private-key` and `app.jwt.rsa-public-key` straight from these two env vars with no fallback (the app signs/verifies JWTs with an RSA key pair, not a shared secret). If either is missing, Spring fails to resolve the placeholder and the backend container exits immediately on boot — `docker compose logs backend` will show `Could not resolve placeholder 'JWT_RSA_PRIVATE_KEY'`. Generate a real PKCS8/X.509 key pair for local dev and set both as single-line base64 values in `.env`; do not reuse the `.env.example` placeholders.

- **Flyway runs on startup.** The first `docker compose up` will apply all Flyway migrations. If the migrations include DDL that takes time (e.g. creating GIN indexes on large tables), the backend healthcheck may time out. Increase `retries` or `start_period` in the backend healthcheck if this happens.

- **Volumes persist data between restarts.** `docker compose down` stops containers but keeps the `postgres_data` and `minio_data` volumes. To start completely fresh (useful when testing migrations or resetting dev state), use `docker compose down -v`.

- **`VITE_API_BASE_URL` must point to where the browser can reach the backend.** Inside the Docker network, `backend` is a valid hostname; from the browser on the host machine, the backend is at `http://localhost:8080`. Because Vite bakes this value into the JS bundle, the value used at build time must be the URL the browser (not another container) uses to reach the API. For local development, `http://localhost:8080` is correct.

- **Windows line endings in `.env`.** If `.env` was created in a Windows text editor, it may have CRLF line endings. Some Docker versions on Windows handle this fine; others do not. If environment variables appear empty inside the container, convert the file to LF: in VS Code, click `CRLF` in the status bar and select `LF`, then save.

- Related tasks: [TASK-10.44](TASK-10.44-dockerfile-backend.md), [TASK-10.45](TASK-10.45-dockerfile-frontend.md), [TASK-10.42](TASK-10.42-smoke-test-script.md), [TASK-10.47](TASK-10.47-cicd-docker-build-push.md).

- Official reference: [Docker Compose file reference](https://docs.docker.com/compose/compose-file/), [Compose `depends_on`](https://docs.docker.com/compose/compose-file/05-services/#depends_on).

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Compose basics** — define multi-container apps in one YAML file — https://docs.docker.com/compose/
- **Services, networks, volumes** — how containers talk and persist data — https://docs.docker.com/reference/compose-file/
- **depends_on & healthchecks** — start order and readiness gating — https://docs.docker.com/reference/compose-file/services/

### Official docs (code reference)
- **Docker Compose documentation** — https://docs.docker.com/compose/
- **Compose file reference** — https://docs.docker.com/reference/compose-file/
