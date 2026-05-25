# TASK-10.25 — Trace one request end-to-end

## Overview

Before adding structured logging or distributed tracing tooling, you need to build the mental model of what a single request actually looks like as it travels through the application layers. This task is a guided reading exercise: send one `POST /api/v1/posts` request, then hunt through the existing logs and identify the controller, service, and persistence-adapter lines that belong to it. The goal is to understand the layered journey — controller receives, service orchestrates, adapter persists — before TASK-10.26 wires a shared `requestId` across all three layers automatically.

No source code is modified in this task. The deliverable is understanding, confirmed by your ability to paste an ordered set of log lines for a single request.

---

## Level

Warm-up · Pairs with [TASK-10.26 — Structured logging & MDC](TASK-10.26-structured-logging-mdc.md)

---

## Why

When something goes wrong in production, the first question is "which lines in the log belong to this one request?" Right now the logs are a jumbled stream of lines from many concurrent requests, with no way to link them. Before you fix that problem with MDC in TASK-10.26, you should experience the problem first-hand: manually trace a single request through the logs and feel how hard it is without a shared identifier. That frustration is the motivation that makes TASK-10.26 feel valuable rather than bureaucratic.

Understanding the controller → service → adapter call chain also sets you up to write meaningful integration tests and to reason about where a failure actually happened when a 500 error comes back.

---

## Prerequisites

- The backend is running locally (`cd backend && mvn spring-boot:run` or via Docker Compose).
- At least one authenticated user exists — you need a valid JWT token to call `POST /api/v1/posts`.
- You have reviewed the `logging.level.com.instagram: DEBUG` setting already present in `backend/src/main/resources/application-local.yml`. This ensures that `DEBUG`-level log lines from the `com.instagram` package are visible in the console.
- You have a terminal open with the backend console output visible (or you can scroll back in the Docker Compose logs).

**Concepts to skim:**

- **Spring Boot layered architecture**: a request passes through Filter → Controller → Service → Persistence Adapter in that order. Each layer has a clearly defined responsibility.
- **SLF4J `log.debug()` / `log.info()`**: the logging framework used in this project. Log statements use parameterized placeholders: `log.info("Created post id={}", post.getId())`, never string concatenation.
- **Thread name in log output**: Spring Boot's default log format includes the thread name (`[http-nio-8080-exec-1]`). All three log lines for a single synchronous request share the same thread name — that is how you manually correlate them before MDC exists.

---

## Files to Create / Modify

```
# No source files are created or modified in this task.
# The only deliverable is your ability to grep the logs and identify
# the ordered set of lines for one request.

backend/src/main/resources/application-local.yml    (read-only reference)
backend/src/main/java/com/instagram/adapter/in/web/PostController.java    (read-only reference)
backend/src/main/java/com/instagram/application/service/PostService.java  (read-only reference)
backend/src/main/java/com/instagram/adapter/out/persistence/PostPersistenceAdapter.java (read-only reference)
```

---

## Step-by-Step

### 1. Start the backend with the local profile active

Open a PowerShell terminal and start the backend. The `local` profile is active by default (see `application.yml`), which sets `logging.level.com.instagram: DEBUG` and `spring.jpa.show-sql: true`.

```powershell
# From the repo root
cd backend
mvn spring-boot:run
```

Wait for the line:

```
Started SocialMediaApplication in X.XXX seconds
```

Keep this terminal visible. All log output appears here.

---

### 2. Obtain a JWT token for an existing user

In a second terminal, call the login endpoint. Replace the placeholder credentials with a user that already exists in your local database:

```powershell
$response = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/auth/login" `
  -ContentType "application/json" `
  -Body '{"usernameOrEmail":"testuser","password":"password123"}'

$token = $response.data.accessToken
Write-Host "Token: $token"
```

If you prefer `curl` via Git Bash:

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"usernameOrEmail":"testuser","password":"password123"}' \
  | python -c "import sys,json; print(json.load(sys.stdin)['data']['accessToken'])")
echo $TOKEN
```

---

### 3. Create a presigned upload URL (required before creating a post)

The `POST /api/v1/posts` endpoint requires at least one media item with a MinIO object key. First generate a presigned upload URL:

```powershell
$upload = Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/posts/upload-url" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body '{"filename":"photo.jpg","contentType":"image/jpeg"}'

$objectKey = $upload.data.objectKey
Write-Host "Object key: $objectKey"
```

---

### 4. Send the POST /api/v1/posts request

```powershell
Invoke-RestMethod -Method Post `
  -Uri "http://localhost:8080/api/v1/posts" `
  -Headers @{ Authorization = "Bearer $token" } `
  -ContentType "application/json" `
  -Body (@{
    caption  = "Tracing end-to-end"
    location = "Test"
    mediaItems = @(@{ objectKey = $objectKey; mediaType = "IMAGE"; displayOrder = 0 })
  } | ConvertTo-Json -Depth 3)
```

Note the timestamp shown in the response — you will use it to find the right block of log lines.

---

### 5. Find the log lines in the console

Look in the backend terminal window. You are looking for three clusters of lines that all share the **same thread name** (e.g., `[http-nio-8080-exec-3]`). They will appear consecutively or nearly so.

What to look for:

```
# Layer 1 — Controller (adapter/in/web/PostController.java)
... [http-nio-8080-exec-3] c.i.adapter.in.web.PostController  : ...

# Layer 2 — Service (application/service/PostService.java)
... [http-nio-8080-exec-3] c.i.application.service.PostService : ...

# Layer 3 — Persistence (SQL logged by Hibernate)
... [http-nio-8080-exec-3] org.hibernate.SQL                  : insert into posts ...
```

> **Tip:** If the controller and service do not currently emit `log.debug()` / `log.info()` calls for post creation, look for the Hibernate SQL lines (`org.hibernate.SQL`) — those are always present because `show-sql: true` is set in the local profile. The SQL lines are your guarantee that the request reached the persistence layer.

---

### 6. Filter logs by thread name

If the console has scrolled past the relevant lines, you can re-run the request and immediately capture output. Alternatively, if you are running via Docker Compose, filter the logs:

```powershell
# PowerShell — filter by the thread name you identified
docker compose logs backend --since 2m | Select-String "exec-3"
```

```bash
# Git Bash equivalent
docker compose logs backend --since 2m | grep "exec-3"
```

Replace `exec-3` with the thread number you observed.

---

### 7. Record the ordered log lines

Write down (or copy into a scratch file) the complete ordered set of log lines from your single request. They should appear in this order:

1. Spring Security filter validates the JWT (visible if `logging.level.org.springframework.security: DEBUG` is set — optional)
2. `PostController` receives the request
3. `PostService` executes the business logic
4. Hibernate SQL: `insert into posts ...`
5. Hibernate SQL: any media or hashtag inserts
6. `PostController` sends the 201 response

Notice the problem you are about to solve in TASK-10.26: if two requests arrive at the same time on threads `exec-3` and `exec-4`, their log lines interleave and you can only separate them by thread name — which is not exposed in production log aggregators like Loki. A `requestId` field solves this.

---

## Checklist

- [ ] Trigger one `POST /api/v1/posts` request against the running local backend
- [ ] Grep the logs for its thread name and identify all lines that belong to that single request
- [ ] Confirm the lines appear in controller → service → adapter order

---

## How to Verify

There is no automated test for this task. Verification is manual.

**Passing result:** You can paste a sequence of log lines, all sharing the same thread name (e.g., `http-nio-8080-exec-3`), that includes at minimum:

1. A line originating from `c.i.adapter.in.web.PostController` (or the Tomcat request-received line)
2. A line from `c.i.application.service.PostService` or an `org.hibernate.SQL` insert statement
3. The Hibernate `insert into posts` SQL line

The lines appear in that order in the log output.

```powershell
# Quick check: does the local profile enable DEBUG logging for com.instagram?
Get-Content backend/src/main/resources/application-local.yml | Select-String "com.instagram"
```

Expected output:

```
    com.instagram: DEBUG
```

---

## Notes / Gotchas

**"I see no log lines from PostController or PostService."**
The controller and service may not currently have explicit `log.debug()` calls. That is fine — the Hibernate SQL lines (`org.hibernate.SQL`) confirm the request reached the persistence layer. TASK-10.26 adds proper `log.info()` calls to all layers as part of the structured-logging work.

**"All my log lines have the same thread name anyway — why is this a problem?"**
On a single-user local machine with no concurrent traffic, you will rarely see interleaving. In production, dozens of requests arrive every second and their log lines mix together. The thread name trick does not survive log aggregation (Loki, Elasticsearch) because the thread pool is shared and thread names repeat.

**"Where is PostPersistenceAdapter in the logs?"**
Hibernate SQL logs are printed by the `org.hibernate.SQL` logger, not by the persistence adapter class directly. The adapter calls the JPA repository, which triggers Hibernate. If you want to see an explicit adapter-level log line, you can temporarily add `log.debug("saving post")` to `PostPersistenceAdapter.save()` — but remove it before committing; TASK-10.26 will add proper structured logging there.

**Next step:** Once you have traced the request manually and felt the pain of correlating lines by thread name, proceed to [TASK-10.26](TASK-10.26-structured-logging-mdc.md) which adds a `requestId` field to every log line automatically.

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **The three pillars of observability** — logs, metrics, traces — https://opentelemetry.io/docs/concepts/observability-primer/
- **Correlation IDs via MDC** — tag every log line of one request with the same id — https://logback.qos.ch/manual/mdc.html
- **Reading the request lifecycle** — filter → controller → service → repository — https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-servlet.html

### Official docs (code reference)
- **SLF4J manual** — https://www.slf4j.org/manual.html
- **Spring Boot Actuator (guide)** — https://spring.io/guides/gs/actuator-service/
