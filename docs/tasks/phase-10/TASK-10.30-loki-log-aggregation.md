# TASK-10.30 — Centralized log aggregation (Loki + Promtail)

## Overview

TASK-10.26 produces structured JSON logs, but they stay in each container's stdout — invisible to anything except `docker compose logs`. This task ships those logs to Grafana Loki, a lightweight log aggregation system designed to work alongside Prometheus. Promtail runs as a sidecar, tails the container logs, parses the JSON fields, and forwards each line to Loki. Grafana (already running from TASK-10.29) gains Loki as a second datasource. After this task, you can open Grafana → Explore, select the Loki datasource, type a LogQL query filtering by `requestId`, and see the complete controller → service → persistence lifecycle of one request — exactly the trace you had to perform manually in TASK-10.25, but now searchable forever across every past run.

---

## Level

Core · Builds on [TASK-10.26 — Structured logging & MDC](TASK-10.26-structured-logging-mdc.md) · Builds on [TASK-10.29 — Grafana dashboards & Prometheus alerting](TASK-10.29-grafana-prometheus-alerting.md)

---

## Why

TASK-10.26 produces structured JSON logs, but they are stranded in each container's stdout. Shipping them to Loki lets you search by `requestId` or `userId` across every instance and run — which is the entire payoff of structured logging in the first place. Without a log aggregator, structured logging is just formatting: you can grep a single container's stdout, but you cannot search across time, across restarts, or across multiple replicas. Loki stores the logs in compressed chunks indexed by label, so a query like `{app="backend"} | json | requestId="f3a2-..."` is fast even across days of log data. Crucially, Loki is a Prometheus-native tool: the same Grafana instance already running for metrics can explore logs, and you can even correlate a Prometheus alert with the log lines that caused it.

---

## Prerequisites

- [TASK-10.26](TASK-10.26-structured-logging-mdc.md) is complete — the backend emits JSON logs with `requestId`, `userId`, `method`, and `path` fields.
- [TASK-10.29](TASK-10.29-grafana-prometheus-alerting.md) is complete — Grafana is running at `http://localhost:3000`.
- Docker Compose is running with the `grafana` service up.

**Concepts to skim:**

- **Loki**: a horizontally scalable log aggregation system from Grafana Labs. Unlike Elasticsearch, it does not index the full text of log lines — it indexes only labels (like `app`, `level`). This makes it cheap to run. Full-text search is done at query time using LogQL.
- **Promtail**: the agent that ships logs from container stdout to Loki. It tails log files (or the Docker log driver), applies label extraction, and forwards lines in batches.
- **LogQL**: Loki's query language. A query has two parts: a log stream selector `{app="backend"}` (label filter, fast) and an optional pipeline `| json | requestId="f3a2-..."` (parsed filter, applied to matching lines). Keep label cardinality low — never use `requestId` as a label, only as a pipeline filter.
- **Label cardinality**: Loki indexes by labels. High-cardinality labels (one unique value per request, like `requestId`) blow up the index. Promote only low-cardinality fields to labels: `app`, `level`, `env`. Filter high-cardinality fields in the pipeline with `| json |`.
- **Grafana Explore**: the ad-hoc query UI in Grafana for both Prometheus (PromQL) and Loki (LogQL). You do not need a dashboard panel for one-off investigation.

---

## Files to Create / Modify

```
docker-compose.yml                                                    (modify)
promtail/promtail-config.yml                                          (new)
docs/infra/grafana/provisioning/datasources/loki.yml                  (new)
docs/infra/observability-setup.md                                     (modify — add Loki section)
```

---

## Step-by-Step

### 1. Configure the Docker log driver so Promtail can tail logs

By default, Docker writes container stdout to JSON log files on the host. Promtail reads these files. You need to tell Docker Compose to use the `json-file` log driver (the default on most systems) and give Promtail access to the log directory.

No change is needed if you are using Docker Desktop on Windows — it already uses `json-file`. Verify by running:

```powershell
docker inspect instagram-social-backend-1 --format '{{.HostConfig.LogConfig.Type}}'
```

Expected: `json-file`

If the output is `local` or another driver, add the following to the `backend` service in `docker-compose.yml` when you add the backend service (in TASK-10.44/10.46):

```yaml
    logging:
      driver: json-file
      options:
        max-size: "50m"
        max-file: "3"
```

For now, since the backend runs via `mvn spring-boot:run` on the host (not as a Docker container), Promtail will tail a log file written by the Spring Boot process. See step 3 for how to write logs to a file in the local profile.

---

### 2. Add Loki and Promtail services to docker-compose.yml

Open `docker-compose.yml` and add two new services after the `grafana` service:

```yaml
  loki:
    image: grafana/loki:2.9.8
    restart: always
    ports:
      - "3100:3100"
    command: -config.file=/etc/loki/local-config.yaml
    volumes:
      - loki_data:/loki
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:3100/ready || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5

  promtail:
    image: grafana/promtail:2.9.8
    restart: always
    volumes:
      - ./promtail/promtail-config.yml:/etc/promtail/config.yml:ro
      # Mount the host log directory so Promtail can read container logs
      - /var/lib/docker/containers:/var/lib/docker/containers:ro
      # On Windows with Docker Desktop, Docker log files are under the WSL2 filesystem.
      # The path above works inside the Linux VM; Docker Desktop mounts it automatically.
    command: -config.file=/etc/promtail/config.yml
    depends_on:
      - loki
```

Add `loki_data` to the `volumes` section at the bottom:

```yaml
volumes:
  postgres_data:
  minio_data:
  prometheus_data:
  grafana_data:
  loki_data:      # <-- add
```

---

### 3. Write the backend log to a file (for local non-Docker runs)

When the backend runs via `mvn spring-boot:run` on the host, its logs go to stdout only. Promtail tails files, not stdout directly. Add a file appender to `logback-spring.xml` for the `local` profile so Promtail has something to read:

Open `backend/src/main/resources/logback-spring.xml` and update the `local` profile section:

```xml
<springProfile name="local">

  <!-- Console appender — existing, unchanged -->
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>
        %d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [rid=%X{requestId} uid=%X{userId} trace=%X{traceId} span=%X{spanId}] - %msg%n
      </pattern>
    </encoder>
  </appender>

  <!-- File appender — JSON format so Promtail can parse it -->
  <appender name="FILE_JSON" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/instagram.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
      <fileNamePattern>logs/instagram.%d{yyyy-MM-dd}.log</fileNamePattern>
      <maxHistory>7</maxHistory>
    </rollingPolicy>
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <customFields>{"app":"backend","env":"local"}</customFields>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE_JSON"/>
  </root>

  <logger name="com.instagram" level="DEBUG"/>
  <logger name="org.hibernate.SQL" level="DEBUG"/>
  <logger name="org.springframework.web" level="INFO"/>
  <logger name="org.springframework.security" level="INFO"/>
</springProfile>
```

This writes JSON log lines to `backend/logs/instagram.log` in addition to the console. The `logs/` directory is created automatically by Logback.

Add `backend/logs/` to `.gitignore` so log files are not committed:

```powershell
Add-Content -Path .gitignore -Value "backend/logs/"
```

---

### 4. Create promtail/promtail-config.yml

Create the directory and config file:

```powershell
New-Item -ItemType Directory -Force -Path promtail
```

Create `promtail/promtail-config.yml`:

```yaml
server:
  http_listen_port: 9080
  grpc_listen_port: 0

positions:
  filename: /tmp/promtail-positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:

  # --- Scrape backend log file (for local mvn spring-boot:run) ---
  - job_name: instagram-backend-file
    static_configs:
      - targets:
          - localhost
        labels:
          app: backend
          env: local
          # __path__ tells Promtail which file to tail
          __path__: /host-logs/instagram.log

    pipeline_stages:
      # Parse the JSON line written by LogstashEncoder
      - json:
          expressions:
            level: level
            requestId: requestId
            userId: userId
            logger: logger_name
            traceId: traceId
      # Promote 'level' to a Loki label (low cardinality: DEBUG, INFO, WARN, ERROR)
      - labels:
          level:
      # DO NOT promote requestId or userId to labels — high cardinality!

  # --- Scrape Docker container logs (for when backend runs in Docker, TASK-10.46) ---
  - job_name: instagram-docker
    docker_sd_configs:
      - host: unix:///var/run/docker.sock
        refresh_interval: 5s
        filters:
          - name: label
            values: ["com.docker.compose.service=backend"]
    relabel_configs:
      - source_labels: [__meta_docker_container_name]
        target_label: container
      - source_labels: [__meta_docker_compose_service]
        target_label: service
      - replacement: backend
        target_label: app
    pipeline_stages:
      - json:
          expressions:
            level: level
            requestId: requestId
            userId: userId
            traceId: traceId
      - labels:
          level:
```

The file-based scrape mounts the host log path inside the Promtail container. Add the volume mount to the `promtail` service in `docker-compose.yml`:

```yaml
  promtail:
    # ... existing config ...
    volumes:
      - ./promtail/promtail-config.yml:/etc/promtail/config.yml:ro
      - /var/lib/docker/containers:/var/lib/docker/containers:ro
      - ./backend/logs:/host-logs:ro    # <-- add: exposes the file appender output
```

---

### 5. Add Loki as a provisioned Grafana datasource

Create `docs/infra/grafana/provisioning/datasources/loki.yml`:

```yaml
apiVersion: 1

datasources:
  - name: Loki
    type: loki
    url: http://loki:3100
    access: proxy
    isDefault: false
    jsonData:
      maxLines: 1000
      # Link log lines to Zipkin traces via the traceId field (TASK-10.28)
      derivedFields:
        - datasourceUid: ''        # Leave empty for external URL; set to Zipkin datasource UID if added
          matcherRegex: '"traceId":"([a-f0-9]+)"'
          name: TraceID
          url: 'http://localhost:9411/zipkin/traces/${__value.raw}'
```

---

### 6. Start the Loki stack

```powershell
docker compose up -d loki promtail
```

Wait 15–20 seconds for Loki to initialize and Promtail to discover the log file. Then restart the backend so new log lines are written to `backend/logs/instagram.log`:

```powershell
cd backend
mvn spring-boot:run
```

---

### 7. Send a request and query Loki in Grafana

Send one `POST /api/v1/posts` request to the running backend. Note the `requestId` from the `X-Request-Id` response header or from the console log.

Open Grafana at `http://localhost:3000` → **Explore** (compass icon in the left sidebar). Select **Loki** from the datasource dropdown.

In the query box, enter a log stream selector:

```logql
{app="backend"}
```

Click **Run query**. You should see log lines appearing in the results panel. The lines are rendered as JSON.

Now filter to the specific request:

```logql
{app="backend"} | json | requestId="<paste-your-requestId-here>"
```

**Passing result:** You see exactly the log lines belonging to that request, in time order: the `PostService` debug line first, then the Hibernate SQL, then the `PostController` info line.

---

## Checklist

- [ ] Add `loki` + `promtail` services to `docker-compose.yml` (Promtail tails the container logs)
- [ ] Configure Promtail to parse the JSON format from `logback-spring.xml` and promote `requestId`, `userId`, and `level` to labels (keep label cardinality low — no per-request labels)
- [ ] Add Loki as a provisioned Grafana datasource
- [ ] Verify a `{app="backend"} | json | requestId="…"` query returns the controller → service → adapter lines for one request
- [ ] (Optional) Link logs ↔ traces: derive a Grafana field from `traceId` (TASK-10.28) that jumps to the Zipkin span

---

## How to Verify

**1. Loki is healthy:**

```powershell
Invoke-RestMethod -Uri "http://localhost:3100/ready"
```

Expected: `ready`

**2. Promtail has discovered the log file:**

```powershell
Invoke-RestMethod -Uri "http://localhost:9080/targets"
```

The response should list the `instagram-backend-file` job with `state: active`.

**3. Log lines appear in Grafana Explore:**

Open `http://localhost:3000` → Explore → Loki. Run:

```logql
{app="backend"} | json
```

At least a few log lines should appear in the results table.

**4. Single-request filter works:**

Copy a `requestId` value from the backend console (the `rid=` field). Run:

```logql
{app="backend"} | json | requestId="<your-requestId>"
```

The result must include lines with `logger_name` values from at least two different classes (e.g., `PostService` and `PostController`), confirming that MDC propagated the `requestId` across layers.

**5. `level` label filtering works:**

```logql
{app="backend", level="ERROR"} | json
```

If no errors have occurred, the result is empty — which is also a passing result. Trigger a 404 or 500 and retry.

---

## Notes / Gotchas

**"Loki returns 'no streams found' for my query."**
Check that Promtail is running and has found the log file. Visit `http://localhost:9080/targets`. If the file-based job is missing, verify that the `./backend/logs:/host-logs:ro` volume mount exists in `docker-compose.yml` and that `backend/logs/instagram.log` exists on the host (it is created by Logback on the first log line after startup).

**"I accidentally promoted `requestId` to a Loki label."**
Remove it from the `labels:` block in `promtail-config.yml` and restart Promtail. High-cardinality labels (unique per request) cause Loki to create millions of streams, which degrades performance and eventually causes OOM errors. The correct pattern is: low-cardinality fields (`app`, `env`, `level`) become labels; everything else is queried via `| json |` pipeline filters.

**"The derived field link to Zipkin does not work."**
The `matcherRegex` must match the exact field name in the JSON log line. Verify the field is called `traceId` (not `trace_id`) in your `logback-spring.xml` JSON output. Also confirm that the Zipkin UI URL format is correct for your Zipkin version: v2 uses `/zipkin/traces/<traceId>`.

**"On Windows with Docker Desktop, `/var/lib/docker/containers` is not accessible."**
Docker Desktop on Windows runs containers inside a WSL2 VM. The container log files are inside the VM, not on the Windows filesystem. The `./backend/logs:/host-logs:ro` volume mount in step 4 (using the file appender) avoids this problem entirely for local development. The Docker SD config (for when the backend runs in a container in TASK-10.46) will work normally because Promtail runs inside Docker and can access the Docker socket.

**"Grafana shows 'Loki datasource cannot connect'."**
Confirm the Loki container is healthy (`docker compose ps loki`). The Grafana datasource uses `http://loki:3100` — this Docker service name resolves within the Docker network. If Grafana and Loki are on different Docker networks, add both to a shared network in `docker-compose.yml`.

**References:**
- [Loki quickstart](https://grafana.com/docs/loki/latest/get-started/)
- [Promtail configuration](https://grafana.com/docs/loki/latest/send-data/promtail/configuration/)
- [LogQL query language](https://grafana.com/docs/loki/latest/query/)
- [Grafana datasource provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/#data-sources)

**Cross-task references:**
- [TASK-10.26](TASK-10.26-structured-logging-mdc.md) — the MDC filter and JSON format that makes these queries possible
- [TASK-10.28](TASK-10.28-distributed-tracing.md) — the `traceId` field that links a Loki log line to a Zipkin span
- [TASK-10.29](TASK-10.29-grafana-prometheus-alerting.md) — the Grafana instance that Loki is added to as a second datasource
