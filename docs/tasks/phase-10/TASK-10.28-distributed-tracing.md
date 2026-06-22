# TASK-10.28 — Distributed tracing

## Overview

Add distributed tracing to the backend using OpenTelemetry and Zipkin. Spring Boot Micrometer Tracing generates a `traceId` and `spanId` for every incoming request and injects them into the log output automatically. A Zipkin server (added to `docker-compose.yml`) receives the completed spans and draws them as a waterfall diagram showing how long each layer took. After this task, you can paste a `traceId` into the Zipkin UI and see the full timeline of one request — from controller entry to database return — with millisecond-level timing for each span.

---

## Level

Core · Pairs with [TASK-10.27 — Actuator & Micrometer](TASK-10.27-actuator-micrometer.md) · Builds toward [TASK-10.32 — Frontend RUM & Sentry](TASK-10.32-frontend-rum-sentry.md)

---

## Why

When a request crosses several methods or (in the future) several services, a single log file shows you what happened but not *where the time went*. Was the 800 ms response time caused by a slow database query, a sluggish network call to MinIO, or a CPU-intensive operation in the service layer? Distributed tracing wraps each logical step in a timed "span" and links all the spans for one request under a single "trace." Looking at the Zipkin waterfall chart, you can see at a glance that the `PostPersistenceAdapter.save` span took 700 ms while everything else was under 10 ms — immediately pointing you at the right place to optimize. In a future microservices setup, the same `traceId` propagates across service boundaries via HTTP headers, so you can follow a request from the API gateway through every downstream service.

---

## Prerequisites

- [TASK-10.27](TASK-10.27-actuator-micrometer.md) is complete — Actuator and Micrometer are on the classpath.
- Docker Compose is running and you can add new services to `docker-compose.yml`.
- The backend runs and the local profile is active.

**Concepts to skim:**

- **Trace**: the complete record of one request's journey through the system, identified by a globally unique `traceId`.
- **Span**: one timed operation within a trace — for example, "execute SQL" or "call MinIO." Spans are nested: a parent span represents the whole HTTP request; child spans represent individual operations within it.
- **`traceId` vs `spanId`**: `traceId` is the same across all spans belonging to one request (the root to leaf chain). `spanId` identifies one individual operation. Use `traceId` to find a request; use `spanId` to pinpoint an operation within it.
- **Sampling**: in high-traffic production systems you do not trace every request — the overhead and storage cost is too high. A sampling probability of `1.0` means "trace everything" (fine for dev); `0.1` means "trace 10% of requests" (more typical for prod).
- **W3C TraceContext**: the standard HTTP header format (`traceparent`) for propagating trace context between services. Spring's Micrometer Tracing uses this by default.

---

## Files to Create / Modify

```
backend/pom.xml                                           (modify)
backend/src/main/resources/application.yml                (modify)
backend/src/main/resources/application-local.yml          (modify)
backend/src/main/resources/logback-spring.xml             (modify — add traceId to log pattern)
docker-compose.yml                                        (modify — add zipkin service)
```

---

## Step-by-Step

### 1. Add tracing dependencies to pom.xml

Open `backend/pom.xml` and add the following three dependencies inside `<dependencies>`. All three are managed by the Spring Boot BOM, so no versions are needed:

```xml
<!-- Micrometer Tracing bridge — integrates tracing with Actuator -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>

<!-- OpenTelemetry exporter — sends spans to Zipkin in the Zipkin format -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-zipkin</artifactId>
</dependency>

<!-- Required by the OTel exporter for the Zipkin HTTP sender -->
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

> **Spring Boot version note:** Spring Boot 3.3.x ships a compatible set of these versions in its BOM. Do not specify version numbers — let the BOM manage them to avoid classpath conflicts.

---

### 2. Configure tracing in application.yml

Add a `management.tracing` block to `backend/src/main/resources/application.yml`, at the same level as the existing `management.endpoints` block you added in TASK-10.27:

```yaml
management:
  # ... existing endpoints and metrics config from TASK-10.27 ...
  tracing:
    sampling:
      probability: 1.0    # Trace every request in dev; set to 0.1 or lower in prod
```

Also add the Zipkin exporter URL. Still in `application.yml`:

```yaml
spring:
  # ... existing spring config ...
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

> `${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}` uses a default value for local development. In production, set the `ZIPKIN_ENDPOINT` environment variable to point at your Zipkin or Jaeger collector.

---

### 3. Override sampling probability for the local profile

In `backend/src/main/resources/application-local.yml`, ensure tracing is set to 100% sampling so no requests are missed during development:

```yaml
management:
  tracing:
    sampling:
      probability: 1.0
```

---

### 4. Add traceId and spanId to the logback-spring.xml pattern

Open `backend/src/main/resources/logback-spring.xml` (created in TASK-10.26) and update the `local` profile encoder pattern to include `traceId` and `spanId`. Micrometer Tracing populates these as MDC keys automatically:

```xml
<springProfile name="local">
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>
        %d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [rid=%X{requestId} uid=%X{userId} trace=%X{traceId} span=%X{spanId}] - %msg%n
      </pattern>
    </encoder>
  </appender>
  <!-- ... rest unchanged ... -->
</springProfile>
```

For the JSON (non-local) profile, `logstash-logback-encoder` already includes all MDC keys automatically, so `traceId` and `spanId` will appear in the JSON output without any additional configuration.

---

### 5. Add the Zipkin service to docker-compose.yml

Open `docker-compose.yml` in the repository root and add the `zipkin` service alongside the existing `postgres`, `minio`, and `redis` services:

```yaml
  zipkin:
    image: openzipkin/zipkin:3
    restart: always
    ports:
      - "9411:9411"
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:9411/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
```

> The `openzipkin/zipkin:3` image is self-contained: it runs an in-memory store by default, which is ideal for local development. Data is lost on container restart, which is fine — you only need to trace requests made in the current session.

Start the updated stack:

```powershell
docker compose up -d zipkin
```

Verify Zipkin started:

```powershell
Invoke-RestMethod -Uri "http://localhost:9411/health"
```

Expected response:

```json
{"status":"UP"}
```

---

### 6. Restart the backend and send a traced request

```powershell
cd backend
mvn spring-boot:run
```

Once started, send a `POST /api/v1/posts` request (using the token from earlier tasks). Watch the console for the new `trace=` field in the log output:

```
14:32:01.123 [http-nio-8080-exec-3] INFO  c.i.adapter.in.web.PostController [rid=f3a2-... uid=550e-... trace=4bf9... span=5b4a...] - Post created id=...
```

---

### 7. Open Zipkin and find the trace

Open the Zipkin UI in your browser:

```
http://localhost:9411
```

Click **Run Query** (the blue button). After sending one or more requests, you should see entries appear in the trace list. Click any trace to open the waterfall view, which shows:

- The top-level span for the HTTP request (e.g., `POST /api/v1/posts`)
- Child spans for the JPA/Hibernate operations
- Duration in milliseconds for each span

---

## Checklist

- [x] Add `io.micrometer:micrometer-tracing-bridge-otel` + `opentelemetry-exporter-zipkin` to `pom.xml`
- [x] Configure `application.yml` → `management.tracing.sampling.probability=1.0` for dev
- [x] Run Zipkin locally via Docker Compose (`openzipkin/zipkin` image)

---

## How to Verify

**1. Zipkin UI shows the request as a trace:**

Open `http://localhost:9411`, click **Run Query**, and confirm that at least one trace appears with the service name `instagram` (the `spring.application.name`). Click the trace — the waterfall must show a parent span for the HTTP endpoint and at least one child span for the database operation.

**2. traceId appears in the backend log output:**

In the backend console, confirm that a log line includes the `trace=` field and the value is a non-empty hex string (16+ characters):

```
[rid=... uid=... trace=4bf92f3577b34da6 span=00f067aa0ba902b7]
```

**3. The same traceId is in both the log and the Zipkin UI:**

Copy the `trace=` value from the console log. Paste it into the Zipkin search box (top right, "Paste a trace ID or URL") and press Enter. The matching trace should appear.

**4. Spans include the database operation:**

Click into the trace. The list of spans should include a span whose name starts with `SELECT` or `INSERT` (from Hibernate), confirming that the JDBC instrumentation is working.

---

## Notes / Gotchas

**"No traces appear in Zipkin UI after sending requests."**
Check that the Zipkin container is running (`docker compose ps`) and that the backend can reach it. The default URL is `http://localhost:9411/api/v2/spans`. If the backend is running inside Docker, use the Docker service name (`http://zipkin:9411/api/v2/spans`) instead of `localhost`.

**"I see `trace=0000000000000000` in the logs — all zeros."**
The tracing bridge is on the classpath but no sampler is active. This usually means the `micrometer-tracing-bridge-otel` dependency is present but the `opentelemetry-exporter-zipkin` is missing, so the bridge falls back to a no-op. Verify all three dependencies from step 1 are in `pom.xml` and that `mvn dependency:tree` shows them resolved.

**"The `zipkin-reporter-brave` vs `zipkin-sender-okhttp3` — which do I need?"**
`zipkin-reporter-brave` is the correct transitive reporter for the OpenTelemetry → Zipkin path via `micrometer-tracing-bridge-otel`. The `zipkin-sender-okhttp3` is needed for the older Brave-only path. Stick with `zipkin-reporter-brave` as listed in step 1.

**"Sampling probability is 1.0 — is that safe in production?"**
No. At 1.0, every single request generates spans that are sent to Zipkin. At high traffic, this adds measurable CPU and network overhead and fills Zipkin's storage rapidly. Set `probability: 0.05` to `0.1` (5–10%) for production workloads. Use `1.0` only locally.

**"I want to add custom spans inside my service methods."**
Inject `io.micrometer.tracing.Tracer` and use `tracer.nextSpan().name("my-operation").start()`. This is an advanced step; the automatic HTTP + JDBC spans from this task are sufficient for the Grafana dashboard in TASK-10.29.

**References:**
- [Micrometer Tracing docs](https://docs.micrometer.io/tracing/reference/)
- [Spring Boot Actuator tracing configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/actuator.html#actuator.micrometer-tracing)
- [Zipkin quickstart](https://zipkin.io/pages/quickstart)
- [OpenTelemetry Java SDK](https://opentelemetry.io/docs/languages/java/)

**Cross-task references:**
- [TASK-10.27](TASK-10.27-actuator-micrometer.md) — Actuator must be configured before tracing works
- [TASK-10.29](TASK-10.29-grafana-prometheus-alerting.md) — Grafana can link log entries to Zipkin traces via the `traceId` field
- [TASK-10.32](TASK-10.32-frontend-rum-sentry.md) — propagates `traceId` from the frontend to backend so a failed React render links to its server span

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Traces & spans** — how a request is broken into timed units — https://opentelemetry.io/docs/concepts/signals/traces/
- **Context propagation (W3C Trace Context)** — the `traceparent` header across services — https://www.w3.org/TR/trace-context/
- **Micrometer Tracing** — Spring Boot 3's tracing bridge — https://docs.micrometer.io/tracing/reference/

### Official docs (code reference)
- **OpenTelemetry documentation** — https://opentelemetry.io/docs/
- **Jaeger (trace backend)** — https://www.jaegertracing.io/docs/
