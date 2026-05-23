# TASK-10.29 — Grafana dashboards & Prometheus alerting

## Overview

TASK-10.27 made the backend emit metrics to `/actuator/prometheus`. This task wires up the full observability stack: Prometheus scrapes that endpoint on a schedule, Grafana queries Prometheus and renders the data as live panels, and Alertmanager sends a notification when something goes wrong. After this task you will have a dashboard showing request rate, 5xx error rate, p95 latency, JVM heap, and HikariCP connection activity — and an alert that moves to `firing` state when the 5xx error rate crosses its threshold.

---

## Level

Core · Builds on [TASK-10.27 — Actuator & Micrometer](TASK-10.27-actuator-micrometer.md) · Pairs with [TASK-10.31 — SLIs, SLOs & error-budget burn-rate alerts](TASK-10.31-sli-slo-error-budget.md)

---

## Why

TASK-10.27 made Prometheus collect metrics, but nothing graphs them and nobody gets told when they go bad. A dashboard turns scattered counters into an at-a-glance picture of whether the system is healthy right now — you can see in one view that the p95 latency spiked at 14:30 and that it coincided with a pool saturation event. Alert rules turn "the graph looks wrong" into an actual notification: instead of someone noticing the graph, the system tells you. Multi-window burn-rate alerts (formalized in TASK-10.31) give you early warning while there is still time to act, rather than alerting only after the SLO is already broken.

---

## Prerequisites

- [TASK-10.27](TASK-10.27-actuator-micrometer.md) is complete — `/actuator/prometheus` is reachable and returns metric data.
- Docker Compose is running. You are comfortable adding new services to `docker-compose.yml`.
- The backend is accessible from within the Docker network (the Prometheus container must be able to reach the backend's `/actuator/prometheus` endpoint).

**Concepts to skim:**

- **Prometheus scrape**: Prometheus periodically pulls (scrapes) the `/actuator/prometheus` endpoint. Each scrape adds a new data point. The scrape interval controls resolution (15 s is standard for dev).
- **PromQL**: Prometheus Query Language. Used in Grafana panels and alert rules. Key functions: `rate()` (per-second rate over a window), `histogram_quantile()` (compute a percentile from a histogram), `increase()` (total increase over a window).
- **Grafana provisioning**: instead of clicking through the Grafana UI, you can place JSON files in `docs/infra/grafana/` and configure Grafana to load them at startup. This makes the dashboard reproducible and version-controlled.
- **Alertmanager**: a separate Prometheus component that receives firing alerts and routes them to notification channels (Slack, email, PagerDuty). Grafana can also send its own alerts without Alertmanager.
- **`http_server_requests_seconds`**: the Spring Boot Actuator metric that counts and times every HTTP request. It is a Micrometer Timer, which Prometheus stores as a histogram (`_bucket`, `_sum`, `_count`). All latency panels in this task derive from it.

---

## Files to Create / Modify

```
docker-compose.yml                                              (modify)
prometheus/prometheus.yml                                       (new)
prometheus/alerts.yml                                           (new)
alertmanager/alertmanager.yml                                   (new)
docs/infra/grafana/dashboards/instagram-overview.json           (new)
docs/infra/grafana/provisioning/datasources/prometheus.yml      (new)
docs/infra/grafana/provisioning/dashboards/dashboard.yml        (new)
docs/infra/observability-setup.md                               (new)
```

---

## Step-by-Step

### 1. Add Prometheus, Grafana, and Alertmanager to docker-compose.yml

Open `docker-compose.yml` in the repository root and add three new services after the existing `zipkin` service:

```yaml
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
    depends_on:
      - zipkin
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

  grafana:
    image: grafana/grafana:10.4.3
    restart: always
    ports:
      - "3000:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin      # Change in production
      - GF_USERS_ALLOW_SIGN_UP=false
    volumes:
      - grafana_data:/var/lib/grafana
      - ./docs/infra/grafana/provisioning:/etc/grafana/provisioning:ro
      - ./docs/infra/grafana/dashboards:/var/lib/grafana/dashboards:ro
    depends_on:
      - prometheus
```

Add the new named volumes at the bottom of `docker-compose.yml`:

```yaml
volumes:
  postgres_data:
  minio_data:
  prometheus_data:    # <-- add
  grafana_data:       # <-- add
```

---

### 2. Create prometheus/prometheus.yml

Create the directory and file:

```powershell
New-Item -ItemType Directory -Force -Path prometheus
```

Create `prometheus/prometheus.yml`:

```yaml
global:
  scrape_interval: 15s       # How often Prometheus scrapes each target
  evaluation_interval: 15s   # How often alert rules are evaluated

alerting:
  alertmanagers:
    - static_configs:
        - targets:
            - alertmanager:9093

rule_files:
  - /etc/prometheus/alerts.yml

scrape_configs:
  - job_name: 'instagram-backend'
    metrics_path: /actuator/prometheus
    static_configs:
      # 'host.docker.internal' resolves to the host machine from inside Docker
      # on Linux, replace with the actual host IP (e.g. 172.17.0.1)
      - targets: ['host.docker.internal:8080']
    # If the Actuator endpoint requires authentication (TASK-10.27), add:
    # basic_auth:
    #   username: prometheus
    #   password: <password>
    # For local dev you may temporarily open /actuator/prometheus to all:
    # .requestMatchers("/actuator/prometheus").permitAll()
```

> **Windows / Docker Desktop note:** `host.docker.internal` resolves correctly on Docker Desktop for Windows and Mac. On Linux, add `extra_hosts: ["host.docker.internal:host-gateway"]` to the `prometheus` service in `docker-compose.yml`.

**Temporary permission for local Prometheus scraping:**

The Actuator security added in TASK-10.27 requires `ROLE_ADMIN` for `/actuator/prometheus`. For local dev, add one more permitted matcher in `SecurityConfig.java` while Prometheus scraping is set up:

```java
.requestMatchers("/actuator/prometheus").permitAll()   // TODO: restrict with basic_auth in prod
```

---

### 3. Create prometheus/alerts.yml

Create `prometheus/alerts.yml` with three alert rules:

```yaml
groups:
  - name: instagram-alerts
    interval: 30s   # Evaluate this group every 30 s (overrides global)
    rules:

      # --- 1. High 5xx error rate ---
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
            {{ $labels.job }} has a 5xx error rate of {{ humanizePercentage $value }}
            over the last 5 minutes. Investigate recent deployments or database errors.

      # --- 2. High p99 latency ---
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
            {{ humanizeDuration $value }}. Check for slow database queries or
            missing cache hits (TASK-10.3).

      # --- 3. Instance down ---
      - alert: InstanceDown
        expr: up{job="instagram-backend"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Backend instance is down"
          description: "The instagram-backend target has been unreachable for more than 1 minute."
```

---

### 4. Create alertmanager/alertmanager.yml

Create the directory and file:

```powershell
New-Item -ItemType Directory -Force -Path alertmanager
```

Create `alertmanager/alertmanager.yml` with a placeholder receiver. Replace the Slack webhook URL with a real one to enable actual notifications:

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
    # To enable Slack notifications:
    # 1. Create an incoming webhook at https://api.slack.com/messaging/webhooks
    # 2. Replace the URL below with your webhook URL
    # 3. Set SLACK_WEBHOOK_URL as an env var and reference it here
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

### 5. Create Grafana provisioning files

Create the directory structure:

```powershell
New-Item -ItemType Directory -Force -Path "docs/infra/grafana/provisioning/datasources"
New-Item -ItemType Directory -Force -Path "docs/infra/grafana/provisioning/dashboards"
New-Item -ItemType Directory -Force -Path "docs/infra/grafana/dashboards"
```

**`docs/infra/grafana/provisioning/datasources/prometheus.yml`** — auto-provisions the Prometheus datasource:

```yaml
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    url: http://prometheus:9090
    access: proxy
    isDefault: true
    jsonData:
      timeInterval: '15s'
```

**`docs/infra/grafana/provisioning/dashboards/dashboard.yml`** — tells Grafana where to find dashboard JSON files:

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

### 6. Create the Grafana dashboard JSON

Create `docs/infra/grafana/dashboards/instagram-overview.json`. This is a minimal but complete dashboard JSON that Grafana will import automatically on startup. The PromQL queries reference the `http_server_requests_seconds` metric auto-instrumented by Spring Boot Actuator.

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
          "expr": "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{application=\"instagram\"}[5m])) by (le))",
          "legendFormat": "p95",
          "refId": "A"
        },
        {
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
          "expr": "hikaricp_connections_active{application=\"instagram\"}",
          "legendFormat": "Active",
          "refId": "A"
        },
        {
          "expr": "hikaricp_connections_pending{application=\"instagram\"}",
          "legendFormat": "Pending",
          "refId": "B"
        }
      ]
    }
  ]
}
```

---

### 7. Start the full stack

```powershell
docker compose up -d prometheus alertmanager grafana
```

Wait 30 seconds for Prometheus to complete its first scrape. Then open:

| Service | URL | Credentials |
|---|---|---|
| Prometheus | `http://localhost:9090` | none (no auth by default) |
| Alertmanager | `http://localhost:9093` | none |
| Grafana | `http://localhost:3000` | admin / admin |

---

### 8. Verify the dashboard in Grafana

1. Open `http://localhost:3000` and log in with `admin` / `admin`.
2. Navigate to **Dashboards** → **Browse** → you should see **Instagram — Service Overview**.
3. Click the dashboard. Panels should show data if the backend is running and Prometheus has scraped it.
4. Send a few requests to the backend to generate traffic and watch the Request Rate panel update.

---

### 9. Trigger the High5xxErrorRate alert (optional verification)

To verify alerting works, temporarily force a 5xx response rate above 5% by sending many requests to a non-existent endpoint:

```powershell
# Send 100 requests that will return 404 (which is not a 5xx) — adjust to trigger 5xx if needed
1..100 | ForEach-Object {
    try { Invoke-RestMethod -Uri "http://localhost:8080/api/v1/nonexistent" -Headers @{ Authorization = "Bearer $token" } } catch {}
}
```

Wait 2–3 minutes. Check the Prometheus Alerts page at `http://localhost:9090/alerts` — the alert should move from `inactive` to `firing` if the 5xx rate threshold is exceeded. In production, Alertmanager would route this to the Slack webhook.

---

### 10. Document how to open and use the dashboard

Create `docs/infra/observability-setup.md`:

```markdown
# Observability Stack Setup

## Services

| Service | URL | Purpose |
|---|---|---|
| Prometheus | http://localhost:9090 | Metric storage and alerting |
| Alertmanager | http://localhost:9093 | Alert routing and silencing |
| Grafana | http://localhost:3000 | Dashboards (admin/admin locally) |
| Zipkin | http://localhost:9411 | Distributed traces |

## Start the stack

```powershell
docker compose up -d prometheus alertmanager grafana zipkin
```

## Open the dashboard

Navigate to http://localhost:3000 → Dashboards → Instagram — Service Overview.

## Dashboard panels

- **Request Rate**: requests/second per URI, 1-minute rate
- **5xx Error Rate**: server errors per second
- **p95/p99 Latency**: derived from `http_server_requests_seconds` histogram
- **JVM Heap Used**: per memory pool
- **HikariCP Active Connections**: active and pending pool connections

## Alerting

Alert rules live in `prometheus/alerts.yml`. To silence an alert during maintenance,
use the Alertmanager UI at http://localhost:9093.
```

---

## Checklist

- [ ] Add `prometheus`, `grafana`, and `alertmanager` services to `docker-compose.yml`; point Prometheus at `/actuator/prometheus`
- [ ] Provision a Grafana dashboard (JSON under `docs/infra/grafana/`) with panels: request rate, 5xx error rate, p95/p99 latency (from `http_server_requests`), JVM heap, HikariCP active connections
- [ ] Define Prometheus alert rules in `prometheus/alerts.yml`: high 5xx rate, high p99 latency, `instance down`
- [ ] Wire Alertmanager (or Grafana alerting) to a notification channel (Slack webhook / email) — placeholder receiver config is fine
- [ ] Document how to open Grafana and import/verify the dashboard in `docs/infra/`

---

## How to Verify

**1. Prometheus scrapes the backend:**

Open `http://localhost:9090/targets`. The `instagram-backend` target should show `State: UP` and a recent `Last Scrape` time.

**2. Grafana dashboard renders panels:**

Open `http://localhost:3000`, log in, open the **Instagram — Service Overview** dashboard. All five panels should render with data (non-empty graphs). If panels show "No data", check that Prometheus is scraping successfully and that the backend has been running for at least one scrape interval (15 s).

**3. Alert rules are loaded in Prometheus:**

Open `http://localhost:9090/alerts`. You should see `High5xxErrorRate`, `HighP99Latency`, and `InstanceDown` listed as `inactive` (no threshold exceeded yet).

**4. Alertmanager is reachable:**

```powershell
Invoke-RestMethod -Uri "http://localhost:9093/-/healthy"
```

Expected: `OK`

---

## Notes / Gotchas

**"Prometheus cannot reach the backend — target shows 'connection refused'."**
On Docker Desktop for Windows/Mac, use `host.docker.internal:8080` as the target. On Linux (native Docker), add `extra_hosts: ["host.docker.internal:host-gateway"]` to the `prometheus` service or use the host's actual LAN IP address.

**"Grafana shows 'Datasource not found' on the dashboard."**
The provisioning YAML must use `name: Prometheus` (exactly, case-sensitive) and the datasource provisioning file must be mounted at `/etc/grafana/provisioning/datasources/`. Check that the Docker volume mount path is correct in `docker-compose.yml`.

**"The `http_server_requests_seconds` metric does not appear."**
This metric is auto-registered by Spring Boot Actuator when `micrometer-registry-prometheus` is on the classpath (added in TASK-10.27). If it is missing, confirm that at least one HTTP request has been made to the backend since startup — the metric is registered lazily on first use.

**"Alert stays in 'pending' but never fires."**
The `for: 2m` clause means the condition must be true for 2 continuous minutes before the alert fires. Wait the full duration, or temporarily lower `for: 10s` in `alerts.yml` and reload Prometheus (`curl -X POST http://localhost:9090/-/reload`).

**"I want to use Grafana's built-in alerting instead of Alertmanager."**
Grafana 10+ can send alerts directly to Slack/email/PagerDuty without Alertmanager. Configure a **Contact Point** in Grafana → Alerting → Contact Points and attach it to an **Alert Rule** derived from a dashboard panel. This is simpler for small teams; Alertmanager is more powerful for complex routing and silencing.

**References:**
- [Prometheus configuration docs](https://prometheus.io/docs/prometheus/latest/configuration/configuration/)
- [Prometheus alerting rules](https://prometheus.io/docs/prometheus/latest/configuration/alerting_rules/)
- [Grafana provisioning docs](https://grafana.com/docs/grafana/latest/administration/provisioning/)
- [Alertmanager configuration](https://prometheus.io/docs/alerting/latest/configuration/)

**Cross-task references:**
- [TASK-10.27](TASK-10.27-actuator-micrometer.md) — the Prometheus scrape endpoint and custom counters built here
- [TASK-10.30](TASK-10.30-loki-log-aggregation.md) — adds Loki as a second Grafana datasource alongside Prometheus
- [TASK-10.31](TASK-10.31-sli-slo-error-budget.md) — adds recording rules and burn-rate alerts on top of the Prometheus setup
