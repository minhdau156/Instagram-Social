# Spec: Backend Dockerfile (TASK-10.44)

## Objective

Add a production-ready, multi-stage `backend/Dockerfile` for the Spring Boot API.

- **Stage 1 (`build`):** `maven:3.9-eclipse-temurin-21` compiles `backend/` and packages the Spring Boot fat JAR (`mvn package -DskipTests`).
- **Stage 2 (`run`):** `eclipse-temurin:21-jre-alpine` copies only the built JAR and runs it as a non-root user.

**Why:** a single-stage build bakes Maven, the JDK, `~/.m2/`, and the source tree into the shipped image (500+ MB, larger attack surface). The multi-stage build discards the build environment; the final image ships only the JRE + JAR (~150–200 MB).

**User:** whoever builds/deploys the backend container (dev running it locally via `docker run`, and CI/CD in [TASK-10.47](docs/tasks/phase-10/TASK-10.47-cicd-docker-build-push.md)). Not user-facing.

**Depends on:** [TASK-10.41](docs/tasks/phase-10/TASK-10.41-dockerignore.md) `.dockerignore` — already present at `backend/.dockerignore`. ✅
**Feeds into:** [TASK-10.45](docs/tasks/phase-10/TASK-10.45-dockerfile-frontend.md) (frontend Dockerfile), [TASK-10.46](docs/tasks/phase-10/TASK-10.46-docker-compose.md) (Compose stack).

## Confirmed facts (from repo, not assumed)

- `pom.xml` → `artifactId=social-media-backend`, `version=0.0.1-SNAPSHOT`, `java.version=21` → build output is `backend/target/social-media-backend-0.0.1-SNAPSHOT.jar`.
- `backend/src/main/resources/application-prod.yml` already exists → `SPRING_PROFILES_ACTIVE=prod` is a real, usable profile (task doc calls this "optional"; it's a cheap win since the profile is already there).
- `backend/Dockerfile` does not exist yet; `backend/.dockerignore` does.

## Commands

```bash
# Build the image (run from repo root; context is backend/)
docker build -t instagram-backend:local ./backend

# Run standalone (Postgres reachable via host.docker.internal on Docker Desktop)
docker run --rm -d --name backend-test \
  -p 8080:8080 \
  -e SPRING_DATASOURCE_URL="jdbc:postgresql://host.docker.internal:5432/instagram" \
  -e SPRING_DATASOURCE_USERNAME="instagram" \
  -e SPRING_DATASOURCE_PASSWORD="changeme" \
  -e JWT_SECRET="replace-with-a-strong-secret" \
  instagram-backend:local

curl http://localhost:8080/actuator/health
docker exec backend-test whoami        # expect: appuser
docker image ls instagram-backend:local --format "{{.Size}}"   # expect: 150-220MB
docker stop backend-test
```

## Project Structure (touched)

```
backend/Dockerfile        (new)
backend/.dockerignore     (existing, unmodified)
```

No other files change. No application code, no `pom.xml`, no CI config.

## Code Style / Content Contract

The `Dockerfile` follows exactly the structure given in the task doc (this is a "translate the task doc into a file" task, not an open design):

- Two named stages: `AS build`, `AS run`.
- `COPY pom.xml .` + `RUN mvn dependency:go-offline -q` **before** `COPY src ./src`, so the dependency layer caches independently of source changes.
- `RUN mvn package -DskipTests -q` (tests run in CI's `mvn verify`, not in the image build).
- Runtime stage creates and switches to a non-root user (`appuser`/`appgroup`) via `addgroup -S` / `adduser -S`, then `USER appuser`.
- `COPY --from=build /app/target/social-media-backend-0.0.1-SNAPSHOT.jar app.jar`.
- `EXPOSE 8080`.
- `ENTRYPOINT ["java", "-jar", "app.jar"]` — **exec form**, not shell form, so `SIGTERM` reaches the JVM directly for graceful shutdown.
- Comments in the Dockerfile mirror the task doc's inline explanations (why each layer is ordered the way it is) — this is a case where the comments earn their place, since a future maintainer editing the Dockerfile could easily invert the `COPY` order and silently break caching.

## Testing Strategy

No unit/integration tests apply to a Dockerfile. Verification is manual/CLI, per the task doc's "How to Verify" section:

1. `docker build` exits 0.
2. Container starts, `/actuator/health` returns `{"status":"UP"}` (requires a reachable Postgres — dev runs this against their local Postgres via `host.docker.internal`).
3. `docker exec ... whoami` → `appuser` (confirms non-root).
4. Image size in the 150–220 MB range (confirms the JRE-alpine stage, not the JDK/Maven stage, shipped).

These are the acceptance checks for Phase 4 (Implement) — no automated test suite is added for this task.

## Boundaries

- **Always do:** keep `COPY pom.xml .` ahead of `COPY src ./src` (cache correctness); use exec-form `ENTRYPOINT`; run as non-root; keep the image on the `-jre-alpine` variant (not the full JDK) for stage 2.
- **Ask first:** changing the base images (e.g., swapping `eclipse-temurin` for a different JDK/JRE vendor or a `distroless` base), adding a health-check `HEALTHCHECK` instruction not requested by the task, or wiring `SPRING_PROFILES_ACTIVE=prod` as a hardcoded default (vs. leaving it to be set by Compose/CI env).
- **Never do:** run the container as root; use shell-form `ENTRYPOINT`; copy the full source tree or `.git` into the runtime stage; skip `-DskipTests` in the runtime `mvn verify` path used by CI (skipping tests is only for the image-build stage, since no DB is available there).

## Success Criteria

- [ ] `backend/Dockerfile` exists with the two-stage structure above.
- [ ] `docker build -t instagram-backend:local ./backend` succeeds.
- [ ] Resulting image size is 150–220 MB.
- [ ] Container runs as `appuser`, not `root`.
- [ ] `/actuator/health` returns `UP` when run against a reachable Postgres.
- [ ] `SIGTERM` (e.g. `docker stop`) shuts the JVM down gracefully (exec-form entrypoint).

## Open Questions

None — the task doc fully specifies the Dockerfile content, and repo inspection confirmed the artifact name, Java version, and the existing `prod` profile. Ready to move to Plan/Tasks once you confirm.
