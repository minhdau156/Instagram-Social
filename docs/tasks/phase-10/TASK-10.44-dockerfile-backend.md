# TASK-10.44 — Dockerfile: backend

## Overview

Create a production-ready `Dockerfile` for the Spring Boot backend using a two-stage build. The first stage uses a full Maven + JDK image to compile the code and package the fat JAR; the second stage copies only that JAR into a minimal JRE-only Alpine image. The result is an image that can boot the API without requiring Maven, the JDK, or the source tree — it weighs roughly 150 MB instead of 500+ MB, and it exposes far fewer tools to a potential attacker.

## Level

**Core** · Pairs with [TASK-10.41](TASK-10.41-dockerignore.md) (`.dockerignore`), [TASK-10.45](TASK-10.45-dockerfile-frontend.md) (frontend Dockerfile), and [TASK-10.46](TASK-10.46-docker-compose.md) (full Compose stack).

## Why

A single-stage Docker build that just copies the source tree and runs `mvn package` inside the final image ends up with Maven's local repository (`~/.m2/`, several hundred megabytes), the full JDK, and all your source code baked into the layer that gets pushed to a registry and deployed to production. A multi-stage build discards the build environment entirely — the final image contains only the JRE and the packaged JAR. The smaller image ships faster, uses less disk, and has a smaller attack surface because `mvn`, `javac`, and your source code are simply absent.

## Prerequisites

- Docker Desktop installed; verify with `docker --version`.
- `backend/.dockerignore` in place ([TASK-10.41](TASK-10.41-dockerignore.md)) so the build context is small.
- Familiarity with Maven's standard lifecycle: `mvn package -DskipTests` compiles and packages without running tests (tests run separately in CI).
- **Concepts to skim:**
  - _Multi-stage builds_ — a single `Dockerfile` can contain multiple `FROM` instructions; each is a separate build stage. Only the final stage becomes the image. See [Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/).
  - _`ENTRYPOINT` vs `CMD`_ — `ENTRYPOINT` sets the executable; `CMD` provides default arguments. The pattern `ENTRYPOINT ["java", "-jar", "app.jar"]` is the standard for Spring Boot containers.
  - _`eclipse-temurin`_ — the official OpenJDK distribution on Docker Hub, maintained by the Eclipse Adoptium project. The `-jre-alpine` variant includes only the Java Runtime Environment on a minimal Alpine base.

## Files to Create / Modify

```
backend/Dockerfile         (new)
backend/.dockerignore      (must exist — see TASK-10.41)
```

## Step-by-Step

### 1. Confirm the JAR name

The `pom.xml` defines the artifact as:

```xml
<artifactId>social-media-backend</artifactId>
<version>0.0.1-SNAPSHOT</version>
```

Maven's default naming convention produces:

```
backend/target/social-media-backend-0.0.1-SNAPSHOT.jar
```

The Spring Boot Maven plugin repackages this as an executable fat JAR in place. The `Dockerfile` will copy from `target/` inside the build stage, so this path is relative to the build context (`backend/`).

### 2. Create `backend/Dockerfile`

Create the file at `backend/Dockerfile` (next to `pom.xml`):

```dockerfile
# ── Stage 1: build ──────────────────────────────────────────────────────────
# Use the official Maven + JDK 21 image to compile and package the application.
# This stage is discarded after the JAR is produced — it never ships to prod.
FROM maven:3.9-eclipse-temurin-21 AS build

# Set the working directory inside the build container.
WORKDIR /app

# Copy the Maven descriptor first. Docker caches each layer; if pom.xml hasn't
# changed, the dependency-download layer is reused and builds are much faster.
COPY pom.xml .

# Download all declared dependencies (and nothing else) — this layer is cached
# independently of the source code.
RUN mvn dependency:go-offline -q

# Copy the source tree.
COPY src ./src

# Package the application, skipping tests (tests run in CI separately).
RUN mvn package -DskipTests -q

# ── Stage 2: run ────────────────────────────────────────────────────────────
# Use a slim JRE-only Alpine image. No Maven, no JDK, no source code.
FROM eclipse-temurin:21-jre-alpine AS run

# Create a non-root user to run the process.
# Running as root inside a container is a security anti-pattern.
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

WORKDIR /app

# Copy only the fat JAR from the build stage.
COPY --from=build /app/target/social-media-backend-0.0.1-SNAPSHOT.jar app.jar

# Hand ownership of the workdir to the non-root user.
RUN chown -R appuser:appgroup /app

USER appuser

# Document the port the application listens on.
EXPOSE 8080

# Start the Spring Boot application.
# Use the exec form (JSON array) so signals (SIGTERM) are delivered directly
# to the JVM process, allowing graceful shutdown.
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 3. Build the image locally

Run from the **repo root** so the build context is `backend/`:

```powershell
docker build -t instagram-backend:local ./backend
```

Bash:

```bash
docker build -t instagram-backend:local ./backend
```

Expected output (abbreviated):

```
[+] Building 142.3s (14/14) FINISHED
 => [internal] load build definition from Dockerfile
 => [internal] load .dockerignore
 => [internal] load build context
 => => transferring context: 2.87 MB
 => [build 1/5] FROM maven:3.9-eclipse-temurin-21
 => [build 2/5] WORKDIR /app
 => [build 3/5] COPY pom.xml .
 => [build 4/5] RUN mvn dependency:go-offline -q
 => [build 5/5] COPY src ./src
 => [run  1/4] FROM eclipse-temurin:21-jre-alpine
 => [run  2/4] RUN addgroup -S appgroup && adduser -S appuser -G appgroup
 => [run  3/4] COPY --from=build /app/target/social-media-backend-0.0.1-SNAPSHOT.jar app.jar
 => [run  4/4] RUN chown -R appuser:appgroup /app
 => exporting to image
```

The second build (with unchanged source) should be much faster because the dependency layer is cached.

### 4. Verify the image size

```powershell
docker image ls instagram-backend:local
```

Expected output — the final image should be approximately 150–200 MB:

```
REPOSITORY           TAG     IMAGE ID       CREATED         SIZE
instagram-backend    local   a1b2c3d4e5f6   2 minutes ago   178MB
```

Compare this to the base Maven build image (`maven:3.9-eclipse-temurin-21`), which is over 500 MB.

### 5. Run the container in isolation (no Compose)

This confirms the image starts correctly before wiring it into the full Compose stack:

```powershell
docker run --rm `
  -p 8080:8080 `
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://host.docker.internal:5432/instagram" `
  -e SPRING_DATASOURCE_USERNAME="instagram" `
  -e SPRING_DATASOURCE_PASSWORD="changeme" `
  -e JWT_SECRET="replace-with-a-strong-secret" `
  instagram-backend:local
```

If Postgres is running locally (not in Docker), `host.docker.internal` resolves to the host machine from inside the container on Docker Desktop for Windows and Mac.

Expected result: Spring Boot startup logs appear and the application reports `Started SocialMediaApplication` without exceptions.

### 6. Confirm the container is running as a non-root user

```powershell
docker run --rm instagram-backend:local whoami
```

Expected output:

```
appuser
```

## Checklist

- [ ] Create `backend/Dockerfile` (multi-stage):
  - [ ] Stage 1 (`build`): `maven:3.9-eclipse-temurin-21` — `mvn package -DskipTests`
  - [ ] Stage 2 (`run`): `eclipse-temurin:21-jre-alpine` — copy JAR, `EXPOSE 8080`, `ENTRYPOINT`

## How to Verify

1. Build the image:

   ```powershell
   docker build -t instagram-backend:local ./backend
   ```

   Passing result: build completes with exit code 0.

2. Start the container and call the health endpoint (requires Postgres to be reachable):

   ```powershell
   docker run --rm -d --name backend-test `
     -p 8080:8080 `
     -e SPRING_DATASOURCE_URL="jdbc:postgresql://host.docker.internal:5432/instagram" `
     -e SPRING_DATASOURCE_USERNAME="instagram" `
     -e SPRING_DATASOURCE_PASSWORD="changeme" `
     -e JWT_SECRET="replace-with-strong-secret" `
     instagram-backend:local

   # Wait ~10 seconds for Spring Boot to start, then:
   curl http://localhost:8080/actuator/health
   ```

   Passing result: `{"status":"UP"}` (or similar JSON with `"status":"UP"` at the top level).

3. Confirm the running user:

   ```powershell
   docker exec backend-test whoami
   ```

   Passing result: `appuser`.

4. Confirm the image size is under 250 MB:

   ```powershell
   docker image ls instagram-backend:local --format "{{.Size}}"
   ```

   Passing result: a value in the range 150–220 MB.

5. Clean up:

   ```powershell
   docker stop backend-test
   ```

## Notes / Gotchas

- **Layer caching is only effective if `pom.xml` is copied before `src/`.** The `COPY pom.xml .` + `RUN mvn dependency:go-offline` pattern means Docker reuses the downloaded dependencies layer on every build where `pom.xml` has not changed. If you copy the entire directory (`COPY . .`) in one step, any source change invalidates the dependency cache.

- **`-DskipTests` is intentional.** Tests run in the separate CI job (`mvn verify`) where a PostgreSQL service container is available. Inside `docker build` there is no database, so tests that hit the database would fail.

- **`ENTRYPOINT` exec form vs shell form.** Use `["java", "-jar", "app.jar"]` (exec form, JSON array), not `java -jar app.jar` (shell form). The shell form wraps the process in `/bin/sh -c`, which intercepts `SIGTERM` and prevents graceful Spring Boot shutdown.

- **`host.docker.internal`** resolves correctly on Docker Desktop for Windows and macOS. On a Linux host it requires `--add-host=host.docker.internal:host-gateway`. In the full Compose setup (TASK-10.46) the containers use the Docker network, so this is not an issue.

- **Spring Boot active profile in production.** You may want to pass `-e SPRING_PROFILES_ACTIVE=prod` to activate a production profile that disables Swagger UI and sets stricter logging. This is optional for this task but recommended before a real deployment.

- Related tasks: [TASK-10.41](TASK-10.41-dockerignore.md), [TASK-10.45](TASK-10.45-dockerfile-frontend.md), [TASK-10.46](TASK-10.46-docker-compose.md), [TASK-10.47](TASK-10.47-cicd-docker-build-push.md).

- Official reference: [Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/), [eclipse-temurin on Docker Hub](https://hub.docker.com/_/eclipse-temurin).
