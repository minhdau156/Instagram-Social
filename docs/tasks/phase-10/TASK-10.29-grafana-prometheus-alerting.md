# TASK-10.29 — Full Observability Stack: Prometheus + Grafana + Loki + Tempo

## Overview

Wire up the complete LGTM (Loki + Grafana + Tempo + Prometheus) observability stack using Docker Compose with volume-mounted config files. This task also migrates the backend away from Zipkin (set up in TASK-10.28) to Tempo, which receives traces via OTLP — the same protocol used by the OTel exporter already on the classpath. Logs are pushed directly from the Spring Boot logback pipeline to Loki using the `loki-logback-appender` library, so no Promtail sidecar is needed. After this task you have: live dashboards in Grafana showing request rate, error rate, and latency; logs searchable by `requestId` or `traceId`; traces with a waterfall view in Grafana (via Tempo); and full correlation — click a log line to jump to its trace, or a metric exemplar to jump to the span that caused the spike.

---

## Level

Core · Builds on [TASK-10.27 — Actuator & Micrometer](TASK-10.27-actuator-micrometer.md) · Builds on [TASK-10.28 — Distributed tracing](TASK-10.28-distributed-tracing.md) · Supersedes [TASK-10.30 — Loki log aggregation](TASK-10.30-loki-log-aggregation.md) (Loki is now handled here)

---

## Why

Zipkin is a fine standalone trace viewer, but it is a dead end: it cannot correlate traces with logs or metrics. Tempo is Grafana-native — the same Grafana instance can query Prometheus metrics, Loki log lines, and Tempo spans and link them together. When a Grafana alert fires on a metric threshold, you can click through to the Loki logs that match the time window, then click a `traceId` link in a log line to open the Tempo waterfall for that exact request. No context-switching between four different UIs. Pushing logs from logback directly (Loki4j) instead of via Promtail removes the sidecar complexity and works identically whether the backend runs with `mvn spring-boot:run` or inside Docker.

---

## Prerequisites

- [TASK-10.27](TASK-10.27-actuator-micrometer.md) is complete — `/actuator/prometheus` returns metrics.
- [TASK-10.28](TASK-10.28-distributed-tracing.md) is complete — `micrometer-tracing-bridge-otel` is in `pom.xml` and `traceId`/`spanId` appear in logs.
- Docker Compose is running and you are comfortable editing it.

**Stack summary after this task:**

| Tool | Role | Port |
|---|---|---|
| **Prometheus** | Metric storage & alert evaluation | 9090 |
| **Alertmanager** | Alert routing & silencing | 9093 |
| **Loki** | Log aggregation & search | 3100 |
| **Tempo** | Distributed trace backend (OTLP) | 3200 / 4317 / 4318 |
| **Grafana** | Unified dashboard & explore UI | 3000 |

---

## What Changes From Your Current State

You already completed TASK-10.27 and TASK-10.28, so the following is already in place:
- `micrometer-tracing-bridge-otel`, `opentelemetry-exporter-zipkin`, `zipkin-reporter-brave` in `pom.xml`
- `spring.zipkin.tracing.endpoint` in `application.yml` and `application-local.yml`
- Zipkin service in `docker-compose.yml`

This task **replaces** all of that:

| Current | Replace with |
|---|---|
| `opentelemetry-exporter-zipkin` | `opentelemetry-exporter-otlp` |
| `zipkin-reporter-brave` | *(remove — not needed for OTLP)* |
| `spring.zipkin.tracing.endpoint` | `management.otlp.tracing.endpoint` |
| `zipkin` Docker service | `tempo` Docker service |

---

## Files to Create / Modify

```
backend/pom.xml                                                 (modify — swap exporters, add loki4j)
backend/src/main/resources/application.yml                      (modify — swap Zipkin → OTLP endpoint)
backend/src/main/resources/application-local.yml                (modify — swap Zipkin → OTLP endpoint)
backend/src/main/resources/logback-spring.xml                   (modify — add Loki4j appender)
docker-compose.yml                                              (modify — replace Zipkin, add full stack)
prometheus/prometheus.yml                                       (new)
prometheus/alerts.yml                                           (new)
alertmanager/alertmanager.yml                                   (new)
tempo/tempo.yml                                                 (new)
loki/loki-config.yml                                            (new)
docs/infra/grafana/provisioning/datasources/datasources.yml     (new — all three sources)
docs/infra/grafana/provisioning/dashboards/dashboard.yml        (new)
docs/infra/grafana/dashboards/instagram-overview.json           (new)
docs/infra/observability-setup.md                               (new)
```

---

## Step-by-Step

### 1. Update pom.xml — swap Zipkin exporter for OTLP, add Loki4j

Open `backend/pom.xml`. Remove these two dependencies:

```xml
<!-- REMOVE these two -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-zipkin</artifactId>
</dependency>
<dependency>
    <groupId>io.zipkin.reporter2</groupId>
    <artifactId>zipkin-reporter-brave</artifactId>
</dependency>
```

Add in their place:

```xml
<!-- OTLP exporter — sends spans to Tempo via HTTP OTLP (port 4318) -->
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-exporter-otlp</artifactId>
</dependency>

<!-- Loki4j — pushes logback log lines directly to Loki, no Promtail needed -->
<dependency>
    <groupId>com.github.loki4j</groupId>
    <artifactId>loki-logback-appender</artifactId>
    <version>1.5.2</version>
</dependency>
```

> `opentelemetry-exporter-otlp` is managed by the Spring Boot BOM — no version needed. `loki-logback-appender` is not in the BOM, so pin `1.5.2`.

---

### 2. Update application.yml — swap Zipkin endpoint for OTLP

Open `backend/src/main/resources/application.yml`.

**Remove:**
```yaml
spring:
  zipkin:
    tracing:
      endpoint: ${ZIPKIN_ENDPOINT:http://localhost:9411/api/v2/spans}
```

**Add** (at the same `management:` block level as `management.tracing`):
```yaml
management:
  tracing:
    sampling:
      probability: 1.0
  otlp:
    tracing:
      endpoint: ${OTEL_ENDPOINT:http://localhost:4318}/v1/traces
```

The `management.otlp.tracing.endpoint` property is picked up by Spring Boot's `OtlpAutoConfiguration` and wires the `OtlpHttpSpanExporter` automatically when `opentelemetry-exporter-otlp` is on the classpath.

---

### 3. Update application-local.yml — same swap

Open `backend/src/main/resources/application-local.yml`.

**Remove:**
```yaml
spring:
  zipkin:
    tracing:
      endpoint: http://localhost:9411/api/v2/spans
```

**Add:**
```yaml
management:
  otlp:
    tracing:
      endpoint: http://localhost:4318/v1/traces
```

---

### 4. Add Loki4j appender to logback-spring.xml

Open `backend/src/main/resources/logback-spring.xml` and add a `LOKI` appender to **both** the `local` and `!local` profile sections.

The Loki4j appender pushes log lines to Loki in batches. The label pattern must use **low-cardinality fields only** (app, level, env) — never `requestId` or `traceId` as labels.

**Inside the `<springProfile name="local">` section**, add the appender and reference it from the root:

```xml
<springProfile name="local">
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>
        %d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} [rid=%X{requestId} uid=%X{userId} trace=%X{traceId} span=%X{spanId}] - %msg%n
      </pattern>
    </encoder>
  </appender>

  <!-- Push logs to Loki directly from logback — no Promtail needed -->
  <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
      <url>${LOKI_URL:-http://localhost:3100}/loki/api/v1/push</url>
    </http>
    <format>
      <label>
        <!-- Low-cardinality labels only. requestId/traceId go in the message, not here. -->
        <pattern>app=instagram,env=local,level=%level</pattern>
        <readMarkers>false</readMarkers>
      </label>
      <message class="com.github.loki4j.logback.JsonLayout"/>
      <sortByTime>false</sortByTime>
    </format>
  </appender>

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="LOKI"/>
  </root>

  <logger name="com.instagram" level="DEBUG"/>
  <logger name="org.hibernate.SQL" level="DEBUG"/>
  <logger name="org.springframework.web" level="INFO"/>
  <logger name="org.springframework.security" level="INFO"/>
</springProfile>
```

**Inside the `<springProfile name="!local">` section**, add the same LOKI appender (the JSON console appender stays):

```xml
<springProfile name="!local">
  <appender name="JSON_CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <customFields>{"app":"instagram"}</customFields>
    </encoder>
  </appender>

  <appender name="LOKI" class="com.github.loki4j.logback.Loki4jAppender">
    <http>
      <url>${LOKI_URL:-http://localhost:3100}/loki/api/v1/push</url>
    </http>
    <format>
      <label>
        <pattern>app=instagram,env=prod,level=%level</pattern>
        <readMarkers>false</readMarkers>
      </label>
      <message class="com.github.loki4j.logback.JsonLayout"/>
      <sortByTime>false</sortByTime>
    </format>
  </appender>

  <root level="INFO">
    <appender-ref ref="JSON_CONSOLE"/>
    <appender-ref ref="LOKI"/>
  </root>

  <logger name="com.instagram" level="INFO"/>
  <logger name="org.hibernate.SQL" level="WARN"/>
</springProfile>
```

> **`JsonLayout`** serialises the log event as JSON including all MDC fields (`requestId`, `userId`, `traceId`, `spanId`) as top-level keys. This makes LogQL pipeline filters like `| json | traceId="abc123"` work without any custom field extraction.

---

### 5. Create tempo/tempo.yml

Create the directory and config file:

```powershell
New-Item -ItemType Directory -Force -Path tempo
```

Create `tempo/tempo.yml`:

```yaml
stream_over_http_enabled: true

server:
  http_listen_port: 3200
  log_level: info

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

ingester:
  max_block_duration: 5m

compactor:
  compaction:
    block_retention: 1h       # Keep traces for 1 hour in local dev (short = small disk)

metrics_generator:
  registry:
    external_labels:
      source: tempo
      cluster: docker-compose
  storage:
    path: /var/tempo/generator/wal
    remote_write:
      - url: http://prometheus:9090/api/v1/write   # Push span-derived metrics to Prometheus
        send_exemplars: true

storage:
  trace:
    backend: local
    wal:
      path: /var/tempo/wal
    local:
      path: /var/tempo/blocks

overrides:
  defaults:
    metrics_generator:
      processors: [service-graphs, span-metrics]   # Generates RED metrics from spans
```

> **`metrics_generator`** derives request rate, error rate, and duration (RED) metrics from the incoming spans and pushes them to Prometheus via remote-write. This means you can graph `traces_spanmetrics_calls_total` in Grafana without any code change.

---

### 6. Create loki/loki-config.yml

Create the directory and config file:

```powershell
New-Item -ItemType Directory -Force -Path loki
```

Create `loki/loki-config.yml`:

```yaml
auth_enabled: false

server:
  http_listen_port: 3100
  grpc_listen_port: 9096
  log_level: info

common:
  instance_addr: 127.0.0.1
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

query_range:
  results_cache:
    cache:
      embedded_cache:
        enabled: true
        max_size_mb: 100

schema_config:
  configs:
    - from: 2024-01-01
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

ruler:
  alertmanager_url: http://alertmanager:9093

limits_config:
  reject_old_samples: true
  reject_old_samples_max_age: 168h
  allow_structured_metadata: true
```

---

### 7. Create prometheus/prometheus.yml

```powershell
New-Item -ItemType Directory -Force -Path prometheus
```

Create `prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - alertmanager:9093

rule_files:
  - /etc/prometheus/alerts.yml

scrape_configs:
  # Scrape the Spring Boot backend
  - job_name: 'instagram-backend'
    metrics_path: /actuator/prometheus
    static_configs:
      # host.docker.internal resolves to the host machine from inside Docker Desktop (Windows/Mac)
      # On Linux native Docker: replace with actual host IP or add extra_hosts to docker-compose.yml
      - targets: ['host.docker.internal:8080']

  # Scrape Tempo's own metrics (useful for monitoring the tracing pipeline)
  - job_name: 'tempo'
    static_configs:
      - targets: ['tempo:3200']
```

> **Temporary permission note:** The Actuator security from TASK-10.27 requires `ROLE_ADMIN` for `/actuator/prometheus`. For local Prometheus scraping to work without auth, add this matcher in `SecurityConfig.java`:
> ```java
> .requestMatchers("/actuator/prometheus").permitAll()   // TODO: restrict with basic_auth in prod
> ```

---

### 8. Create prometheus/alerts.yml

Create `prometheus/alerts.yml`:

```yaml
groups:
  - name: instagram-alerts
    interval: 30s
    rules:

      - alert: High5xxErrorRate
        expr: |
          (
            sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m]))
            /
            sum(rate(http_server_requests_seconds_count[5m]))
          ) > 0.05
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High 5xx error rate (>5%) for {{ $labels.job }}"
          description: >
            {{ $labels.job }} 5xx error rate is {{ humanizePercentage $value }}
            over the last 5 minutes.

      - alert: HighP99Latency
        expr: |
          histogram_quantile(0.99,
            sum(rate(http_server_requests_seconds_bucket[5m])) by (le, uri)
          ) > 2.0
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "p99 latency > 2 s on {{ $labels.uri }}"
          description: >
            The 99th-percentile response time for {{ $labels.uri }} is
            {{ humanizeDuration $value }}.

      - alert: InstanceDown
        expr: up{job="instagram-backend"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Backend instance is down"
          description: "instagram-backend has been unreachable for more than 1 minute."
```

---

### 9. Create alertmanager/alertmanager.yml

```powershell
New-Item -ItemType Directory -Force -Path alertmanager
```

Create `alertmanager/alertmanager.yml`:

```yaml
global:
  resolve_timeout: 5m

route:
  group_by: ['alertname', 'severity']
  group_wait: 30s
  group_interval: 5m
  repeat_interval: 4h
  receiver: 'slack-placeholder'

receivers:
  - name: 'slack-placeholder'
    # Replace the URL below with a real Slack incoming webhook to enable notifications.
    slack_configs:
      - api_url: 'https://hooks.slack.com/services/PLACEHOLDER/PLACEHOLDER/PLACEHOLDER'
        channel: '#alerts'
        send_resolved: true
        title: '{{ .CommonAnnotations.summary }}'
        text: '{{ .CommonAnnotations.description }}'

inhibit_rules:
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname', 'job']
```

---

### 10. Create Grafana provisioning files

```powershell
New-Item -ItemType Directory -Force -Path "docs/infra/grafana/provisioning/datasources"
New-Item -ItemType Directory -Force -Path "docs/infra/grafana/provisioning/dashboards"
New-Item -ItemType Directory -Force -Path "docs/infra/grafana/dashboards"
```

**`docs/infra/grafana/provisioning/datasources/datasources.yml`** — provisions all three datasources with cross-linking:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    uid: prometheus
    url: http://prometheus:9090
    access: proxy
    isDefault: true
    jsonData:
      timeInterval: '15s'
      exemplarTraceIdDestinations:
        - name: traceID
          datasourceUid: tempo    # Click a metric exemplar → jump to Tempo trace

  - name: Loki
    type: loki
    uid: loki
    url: http://loki:3100
    access: proxy
    jsonData:
      maxLines: 1000
      derivedFields:
        - datasourceName: Tempo
          datasourceUid: tempo
          matcherRegex: '"traceId":"([a-f0-9]+)"'   # Extract traceId from JSON log line
          name: TraceID
          url: '$${__value.raw}'   # Click traceId in a log line → open Tempo trace

  - name: Tempo
    type: tempo
    uid: tempo
    url: http://tempo:3200
    access: proxy
    jsonData:
      tracesToLogsV2:
        datasourceUid: loki     # Click a span in Tempo → open matching Loki logs
        spanStartTimeShift: '-1h'
        spanEndTimeShift: '1h'
        tags:
          - key: 'service.name'
            value: 'app'
      tracesToMetrics:
        datasourceUid: prometheus
      serviceMap:
        datasourceUid: prometheus
      search:
        hide: false
      nodeGraph:
        enabled: true
```

**`docs/infra/grafana/provisioning/dashboards/dashboard.yml`**:

```yaml
apiVersion: 1

providers:
  - name: 'Instagram Dashboards'
    type: file
    disableDeletion: false
    updateIntervalSeconds: 30
    options:
      path: /var/lib/grafana/dashboards
      foldersFromFilesStructure: false
```

---

### 11. Create the Grafana dashboard JSON

Create `docs/infra/grafana/dashboards/instagram-overview.json`:

```json
{
  "id": null,
  "uid": "instagram-overview",
  "title": "Instagram — Service Overview",
  "tags": ["instagram", "backend"],
  "timezone": "browser",
  "schemaVersion": 38,
  "version": 1,
  "refresh": "30s",
  "panels": [
    {
      "id": 1,
      "title": "Request Rate (req/s)",
      "type": "timeseries",
      "gridPos": { "x": 0, "y": 0, "w": 12, "h": 8 },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(rate(http_server_requests_seconds_count{application=\"instagram\"}[1m])) by (uri)",
          "legendFormat": "{{uri}}",
          "refId": "A"
        }
      ]
    },
    {
      "id": 2,
      "title": "5xx Error Rate",
      "type": "timeseries",
      "gridPos": { "x": 12, "y": 0, "w": 12, "h": 8 },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "sum(rate(http_server_requests_seconds_count{application=\"instagram\",status=~\"5..\"}[1m]))",
          "legendFormat": "5xx errors/s",
          "refId": "A"
        }
      ]
    },
    {
      "id": 3,
      "title": "p95 / p99 Latency",
      "type": "timeseries",
      "gridPos": { "x": 0, "y": 8, "w": 12, "h": 8 },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application=\"instagram\"}[5m])) by (le))",
          "legendFormat": "p95",
          "refId": "A"
        },
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket{application=\"instagram\"}[5m])) by (le))",
          "legendFormat": "p99",
          "refId": "B"
        }
      ]
    },
    {
      "id": 4,
      "title": "JVM Heap Used",
      "type": "timeseries",
      "gridPos": { "x": 12, "y": 8, "w": 12, "h": 8 },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "jvm_memory_used_bytes{application=\"instagram\",area=\"heap\"}",
          "legendFormat": "{{id}}",
          "refId": "A"
        }
      ]
    },
    {
      "id": 5,
      "title": "HikariCP Active Connections",
      "type": "timeseries",
      "gridPos": { "x": 0, "y": 16, "w": 12, "h": 8 },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "hikaricp_connections_active{application=\"instagram\"}",
          "legendFormat": "Active",
          "refId": "A"
        },
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "hikaricp_connections_pending{application=\"instagram\"}",
          "legendFormat": "Pending",
          "refId": "B"
        }
      ]
    },
    {
      "id": 6,
      "title": "Posts Created / Likes Added",
      "type": "timeseries",
      "gridPos": { "x": 12, "y": 16, "w": 12, "h": 8 },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "increase(posts_created_total{application=\"instagram\"}[5m])",
          "legendFormat": "posts created (5m)",
          "refId": "A"
        },
        {
          "datasource": { "type": "prometheus", "uid": "prometheus" },
          "expr": "increase(likes_added_total{application=\"instagram\"}[5m])",
          "legendFormat": "likes added (5m)",
          "refId": "B"
        }
      ]
    }
  ]
}
```

---

### 12. Replace Zipkin with the full stack in docker-compose.yml

Open `docker-compose.yml`. Remove the `zipkin` service block entirely. Add the following five services in its place (after the existing `redis` service):

```yaml
  loki:
    image: grafana/loki:3.0.0
    restart: always
    ports:
      - "3100:3100"
    volumes:
      - ./loki/loki-config.yml:/etc/loki/local-config.yaml:ro
      - loki_data:/loki
    command: -config.file=/etc/loki/local-config.yaml
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:3100/ready || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5

  tempo:
    image: grafana/tempo:2.4.1
    restart: always
    ports:
      - "3200:3200"   # Tempo HTTP API
      - "4317:4317"   # OTLP gRPC
      - "4318:4318"   # OTLP HTTP — backend sends spans here
    volumes:
      - ./tempo/tempo.yml:/etc/tempo/tempo.yml:ro
      - tempo_data:/var/tempo
    command: -config.file=/etc/tempo/tempo.yml
    depends_on:
      - prometheus
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:3200/ready || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5

  prometheus:
    image: prom/prometheus:v2.52.0
    restart: always
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - ./prometheus/alerts.yml:/etc/prometheus/alerts.yml:ro
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.retention.time=7d'
      - '--web.enable-lifecycle'
      - '--enable-feature=exemplar-storage'
      - '--enable-feature=remote-write-receiver'
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:9090/-/ready || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5

  alertmanager:
    image: prom/alertmanager:v0.27.0
    restart: always
    ports:
      - "9093:9093"
    volumes:
      - ./alertmanager/alertmanager.yml:/etc/alertmanager/alertmanager.yml:ro
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:9093/-/healthy || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5

  grafana:
    image: grafana/grafana:10.4.3
    restart: always
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
      - GF_USERS_ALLOW_SIGN_UP=false
      - GF_FEATURE_TOGGLES_ENABLE=traceqlEditor
    volumes:
      - grafana_data:/var/lib/grafana
      - ./docs/infra/grafana/provisioning:/etc/grafana/provisioning:ro
      - ./docs/infra/grafana/dashboards:/var/lib/grafana/dashboards:ro
    depends_on:
      - prometheus
      - loki
      - tempo
    healthcheck:
      test: ["CMD-SHELL", "wget -q --spider http://localhost:3000/api/health || exit 1"]
      interval: 10s
      timeout: 5s
      retries: 5
```

Update the `volumes:` block at the bottom of `docker-compose.yml`:

```yaml
volumes:
  postgres_data:
  minio_data:
  prometheus_data:
  grafana_data:
  loki_data:
  tempo_data:
```

---

### 13. Start the stack and verify

Bring up the new stack:

```powershell
docker compose up -d loki tempo prometheus alertmanager grafana
```

Restart the backend to pick up the new OTLP exporter and Loki4j appender:

```powershell
cd backend
mvn spring-boot:run
```

Send a few requests to generate signal, then verify each component.

---

## Checklist

- [ ] `pom.xml` — removed `opentelemetry-exporter-zipkin` + `zipkin-reporter-brave`; added `opentelemetry-exporter-otlp` + `loki-logback-appender:1.5.2`
- [ ] `application.yml` — replaced `spring.zipkin.tracing.endpoint` with `management.otlp.tracing.endpoint`
- [ ] `application-local.yml` — same swap, pointing to `http://localhost:4318/v1/traces`
- [ ] `logback-spring.xml` — Loki4j appender added to both `local` and `!local` profiles
- [ ] `docker-compose.yml` — Zipkin removed; Loki, Tempo, Prometheus, Alertmanager, Grafana added with volume-mounted configs
- [ ] Config files created: `tempo/tempo.yml`, `loki/loki-config.yml`, `prometheus/prometheus.yml`, `prometheus/alerts.yml`, `alertmanager/alertmanager.yml`
- [ ] Grafana provisioning: `datasources.yml` (all 3 sources with cross-links), `dashboard.yml`, `instagram-overview.json`

---

## How to Verify

**1. Prometheus scrapes the backend:**

Open `http://localhost:9090/targets`. The `instagram-backend` target must show `State: UP`.

**2. Traces appear in Grafana → Explore → Tempo:**

Open `http://localhost:3000` → Explore → select **Tempo** datasource → click **Search** tab → set Service Name to `instagram`. Traces should appear after you send a request to the backend.

**3. `traceId` in backend log matches trace in Tempo:**

Copy a `trace=` hex value from the backend console. In Grafana → Explore → Tempo, paste the value in the **TraceID** field. The matching trace waterfall must appear.

**4. Logs appear in Grafana → Explore → Loki:**

Select **Loki** datasource in Explore. Run:
```logql
{app="instagram"} | json
```
Log lines from the backend should appear. Run:
```logql
{app="instagram"} | json | traceId="<paste-traceId>"
```
Only lines from that request should appear.

**5. Click a log line's TraceID link:**

Expand a log line in Loki Explore. A **TraceID** derived field should appear as a clickable link. Click it — it must open the Tempo trace for that request.

**6. Dashboard renders:**

Open `http://localhost:3000` → Dashboards → **Instagram — Service Overview**. All six panels must show data (non-empty graphs) after you send a few requests.

**7. Alert rules loaded in Prometheus:**

Open `http://localhost:9090/alerts`. `High5xxErrorRate`, `HighP99Latency`, and `InstanceDown` must be listed as `inactive`.

---

## Notes / Gotchas

**"Tempo returns 404 for traces — nothing appears in Grafana."**
The backend must be pointing at `http://localhost:4318/v1/traces` (OTLP HTTP, not Zipkin format). Check the backend console for a log line like `Exporting span ...` or an OTel error. Also confirm Tempo is healthy: `Invoke-RestMethod -Uri "http://localhost:3200/ready"` should return `ready`.

**"Loki4j appender throws `Connection refused` on startup."**
Loki4j queues and retries — a short connection failure on startup is harmless. The backend will start normally and resume pushing logs once Loki is up. If logs never arrive, verify `LOKI_URL` resolves correctly and that port 3100 is reachable from the host.

**"The `traceId` link in Loki does not open Tempo."**
The `matcherRegex` in `datasources.yml` is `"traceId":"([a-f0-9]+)"`. This must match the exact JSON key name in the log line. If you changed the MDC key name in `logback-spring.xml`, update the regex to match. Also confirm the Grafana datasource UID `tempo` matches the `uid: tempo` set in the provisioning file.

**"Prometheus shows `connection refused` for `host.docker.internal:8080`."**
On Linux native Docker (not Docker Desktop), `host.docker.internal` does not resolve automatically. Add to the `prometheus` service in `docker-compose.yml`:
```yaml
extra_hosts:
  - "host.docker.internal:host-gateway"
```

**"Tempo metrics_generator remote_write fails."**
This is non-critical on first startup — Tempo retries. It requires Prometheus to have `--enable-feature=remote-write-receiver` in its command args, which is already included in step 12. If you see repeated errors, confirm the flag is present by checking `docker compose logs prometheus`.

**"I still have `spring.zipkin.tracing.endpoint` in my config."**
Spring Boot will try to auto-configure a Zipkin exporter if the property exists but the exporter is not on the classpath. This produces a warning but no failure. Remove the property to keep the config clean.

---

## References

- [Micrometer Tracing OTLP](https://docs.micrometer.io/tracing/reference/reporters/otlp.html)
- [Grafana Tempo documentation](https://grafana.com/docs/tempo/latest/)
- [Grafana Loki documentation](https://grafana.com/docs/loki/latest/)
- [Loki4j logback appender](https://loki4j.github.io/loki-logback-appender/)
- [Grafana datasource provisioning](https://grafana.com/docs/grafana/latest/administration/provisioning/)
- [Prometheus configuration](https://prometheus.io/docs/prometheus/latest/configuration/configuration/)

**Cross-task references:**
- [TASK-10.27](TASK-10.27-actuator-micrometer.md) — Actuator and custom counters (posts, likes, users)
- [TASK-10.28](TASK-10.28-distributed-tracing.md) — Micrometer OTel bridge that produces the traces Tempo stores
- [TASK-10.31](TASK-10.31-sli-slo-error-budget.md) — SLI/SLO recording rules built on top of Prometheus

---

## Learning Resources

### Concepts to learn
- **OpenTelemetry OTLP** — the wire protocol for traces, metrics, and logs — https://opentelemetry.io/docs/specs/otlp/
- **Grafana LGTM stack** — how Loki, Grafana, Tempo, and Mimir/Prometheus fit together — https://grafana.com/go/webinar/getting-started-with-grafana-lgtm-stack/
- **Loki label cardinality** — why you must not put `requestId` in labels — https://grafana.com/docs/loki/latest/get-started/labels/cardinality/
- **Tempo TraceQL** — querying traces by attribute — https://grafana.com/docs/tempo/latest/traceql/
