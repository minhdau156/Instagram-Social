# TASK-10.31 — SLIs, SLOs & error-budget burn-rate alerts

## Overview

This task formalizes the reliability targets for the Instagram backend's most critical endpoints and wires them into Prometheus so that the system alerts you before the reliability target is broken — not after. You will write three things: an SLO document (`docs/infra/slo.md`) stating the target in plain English, Prometheus recording rules that continuously compute the SLI ratios, and multi-window burn-rate alert rules that page you when the error budget is draining too fast. A Grafana panel ties it together with a live gauge of remaining budget.

---

## Level

Core · Builds on [TASK-10.27 — Actuator & Micrometer](TASK-10.27-actuator-micrometer.md) · Builds on [TASK-10.29 — Grafana dashboards & Prometheus alerting](TASK-10.29-grafana-prometheus-alerting.md)

---

## Why

Raw metrics tell you what is happening, not whether it is acceptable. An SLO sets an explicit reliability target — "99.5% of feed requests succeed under 300 ms over a rolling 30 days." The error budget converts that target into a spend: if 99.5% must succeed, then 0.5% can fail, which is roughly 3.6 hours of downtime per 30 days. Multi-window burn-rate alerts page you while there is still budget left to defend: a "fast burn" alert fires within an hour when the budget is depleting thirty times faster than expected, giving you time to roll back before the SLO window closes. A plain "5xx > 5%" threshold alert tells you only that something is wrong right now; a burn-rate alert tells you that right now is costing you days of future budget.

---

## Prerequisites

- [TASK-10.27](TASK-10.27-actuator-micrometer.md) is complete — `http_server_requests_seconds` is being collected by Prometheus.
- [TASK-10.29](TASK-10.29-grafana-prometheus-alerting.md) is complete — Prometheus and Grafana are running; `prometheus/alerts.yml` exists.
- Familiarity with PromQL `rate()` and `histogram_quantile()` from TASK-10.29.

**Concepts to skim:**

- **SLI (Service Level Indicator)**: a quantitative measure of service behaviour. Examples: "fraction of requests that returned 2xx or 3xx" (availability SLI), "fraction of requests that completed under 300 ms" (latency SLI).
- **SLO (Service Level Objective)**: a target value for an SLI over a time window. Example: "99.5% availability over 30 days."
- **Error budget**: `1 - SLO`. If availability SLO is 99.5%, then 0.5% of requests may fail before the budget is exhausted — roughly `0.005 × 30 days × 24 h × 3600 s = 12,960 seconds` of allowed error traffic.
- **Burn rate**: how fast the error budget is being consumed relative to the rate that would exactly exhaust it at the end of the SLO window. A burn rate of 1 means the budget will exactly run out at window end; a burn rate of 30 means the budget depletes 30× faster than that.
- **Multi-window alert**: uses two time windows for the same burn-rate threshold to filter out false positives. The Google SRE Workbook recommends a 1h + 5m window pair for fast burns and a 6h + 30m pair for slow burns.
- **Recording rules**: pre-computed PromQL expressions stored back into Prometheus as new time series. They make complex queries fast and reusable in both alert rules and Grafana panels.

---

## Files to Create / Modify

```
prometheus/alerts.yml                                                         (modify — add burn-rate rules)
prometheus/recording_rules.yml                                                (new)
prometheus/prometheus.yml                                                     (modify — add recording_rules.yml to rule_files)
docs/infra/slo.md                                                             (new)
docs/infra/grafana/dashboards/instagram-slo.json                              (new)
docs/infra/grafana/provisioning/dashboards/dashboard.yml                      (already exists — no change needed)
```

---

## Step-by-Step

### 1. Document the SLOs in docs/infra/slo.md

Create `docs/infra/slo.md`. This document is the source of truth for reliability targets. Writing it first (before the Prometheus config) keeps the engineering decisions separate from their implementation.

```markdown
# Service Level Objectives — Instagram Backend

> Last updated: 2026-05-23
> Owner: Engineering team
> Review cadence: Each phase release

---

## What these SLOs cover

SLOs apply to production (and staging under load test). Local development is out of scope.

## SLI Definitions

### Availability SLI
Fraction of HTTP requests that return a non-5xx response:

    availability_sli = 1 - (rate(5xx responses) / rate(all responses))

### Latency SLI
Fraction of HTTP requests that complete under a defined threshold:

    latency_sli = fraction of requests with latency <= threshold

---

## SLO Targets

| Endpoint group | Availability target | Latency target | Window |
|---|---|---|---|
| Feed (`GET /api/v1/feed`) | 99.5% | 95% under 300 ms | 30 days |
| Profile (`GET /api/v1/users/{username}`) | 99.5% | 95% under 200 ms | 30 days |
| Login (`POST /api/v1/auth/login`) | 99.9% | 95% under 500 ms | 30 days |

### Error budget (30-day window)

| Endpoint group | Availability budget | Calendar equivalent |
|---|---|---|
| Feed | 0.5% of requests | ~3.6 h of 100% outage |
| Profile | 0.5% of requests | ~3.6 h of 100% outage |
| Login | 0.1% of requests | ~43 min of 100% outage |

### Rationale

- **Feed** is the highest-traffic endpoint. 99.5% availability allows normal deployment
  windows (15–30 min) without burning through budget, yet signals real degradation quickly.
- **Profile** has similar traffic characteristics to the feed. Latency threshold is tighter
  (200 ms vs 300 ms) because the page is rendered synchronously on first visit.
- **Login** gets a tighter availability SLO (99.9%) because a login failure blocks the
  entire user session — it has an outsized impact on user experience.

---

## Burn-rate thresholds

Following the Google SRE Workbook multi-window model:

| Burn rate | Time to budget exhaustion | Alert window pair | Severity |
|---|---|---|---|
| 14.4× | ~2 h | 1h + 5m (fast) | critical |
| 6× | ~5 h | 6h + 30m (slow) | warning |

A burn rate of 14.4× means the budget will exhaust in 30d / 14.4 ≈ 2 days.
```

---

### 2. Create prometheus/recording_rules.yml

Recording rules pre-compute the SLI ratios so that Grafana panels and alert rules do not need to run expensive aggregations on every evaluation:

```yaml
groups:
  - name: instagram_sli_recording_rules
    interval: 1m
    rules:

      # ---- Availability SLI: fraction of non-5xx requests ----

      # 5-minute error ratio (all endpoints)
      - record: job:http_requests_errors:ratio_rate5m
        expr: |
          sum(rate(http_server_requests_seconds_count{application="instagram",status=~"5.."}[5m]))
          /
          sum(rate(http_server_requests_seconds_count{application="instagram"}[5m]))

      # 30-minute error ratio
      - record: job:http_requests_errors:ratio_rate30m
        expr: |
          sum(rate(http_server_requests_seconds_count{application="instagram",status=~"5.."}[30m]))
          /
          sum(rate(http_server_requests_seconds_count{application="instagram"}[30m]))

      # 1-hour error ratio
      - record: job:http_requests_errors:ratio_rate1h
        expr: |
          sum(rate(http_server_requests_seconds_count{application="instagram",status=~"5.."}[1h]))
          /
          sum(rate(http_server_requests_seconds_count{application="instagram"}[1h]))

      # 6-hour error ratio
      - record: job:http_requests_errors:ratio_rate6h
        expr: |
          sum(rate(http_server_requests_seconds_count{application="instagram",status=~"5.."}[6h]))
          /
          sum(rate(http_server_requests_seconds_count{application="instagram"}[6h]))

      # ---- Feed-specific: availability SLI ----

      - record: endpoint:feed:error_ratio_rate5m
        expr: |
          sum(rate(http_server_requests_seconds_count{application="instagram",uri=~"/api/v1/feed.*",status=~"5.."}[5m]))
          /
          sum(rate(http_server_requests_seconds_count{application="instagram",uri=~"/api/v1/feed.*"}[5m]))

      # ---- 30-day error budget consumed (approximation using 30d window) ----
      # This is an approximation: Prometheus does not retain 30d of raw data by default.
      # Increase TSDB retention to 35d in prometheus.yml (--storage.tsdb.retention.time=35d)
      # to make this accurate.
      - record: job:error_budget_remaining:ratio
        expr: |
          1 - (
            sum(increase(http_server_requests_seconds_count{application="instagram",status=~"5.."}[30d]))
            /
            sum(increase(http_server_requests_seconds_count{application="instagram"}[30d]))
          ) / 0.005
        # Interpretation: 1.0 = full budget remaining; 0.0 = budget exhausted;
        # negative = SLO already broken.
```

---

### 3. Add recording_rules.yml to prometheus.yml

Open `prometheus/prometheus.yml` and add the recording rules file to the `rule_files` list:

```yaml
rule_files:
  - /etc/prometheus/alerts.yml
  - /etc/prometheus/recording_rules.yml    # <-- add
```

Mount the file into the Prometheus container in `docker-compose.yml`:

```yaml
  prometheus:
    # ... existing config ...
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - ./prometheus/alerts.yml:/etc/prometheus/alerts.yml:ro
      - ./prometheus/recording_rules.yml:/etc/prometheus/recording_rules.yml:ro    # <-- add
      - prometheus_data:/prometheus
```

---

### 4. Add burn-rate alert rules to prometheus/alerts.yml

Append the following group to `prometheus/alerts.yml` (below the existing `instagram-alerts` group):

```yaml
  - name: instagram-slo-burn-rate
    rules:

      # ---- Fast burn: 1h + 5m windows (fires in < 1 hour) ----
      # For a 99.5% SLO, the error budget is 0.5%.
      # A burn rate of 14.4x exhausts the 30-day budget in ~2 days.
      # threshold = burn_rate × (1 - SLO) = 14.4 × 0.005 = 0.072
      - alert: SLOFeedFastBurn
        expr: |
          job:http_requests_errors:ratio_rate1h > (14.4 * 0.005)
          and
          job:http_requests_errors:ratio_rate5m > (14.4 * 0.005)
        for: 2m
        labels:
          severity: critical
          slo: feed-availability
        annotations:
          summary: "Fast error budget burn rate (feed availability SLO)"
          description: >
            The error rate has been above 7.2% for the last 1 hour and 5 minutes.
            At this rate the 30-day error budget will be exhausted in approximately 2 days.
            Current 1h error ratio: {{ $value | humanizePercentage }}.

      # ---- Slow burn: 6h + 30m windows (fires in < 6 hours) ----
      # A burn rate of 6x exhausts the budget in ~5 days.
      # threshold = 6 × 0.005 = 0.03
      - alert: SLOFeedSlowBurn
        expr: |
          job:http_requests_errors:ratio_rate6h > (6 * 0.005)
          and
          job:http_requests_errors:ratio_rate30m > (6 * 0.005)
        for: 15m
        labels:
          severity: warning
          slo: feed-availability
        annotations:
          summary: "Slow error budget burn rate (feed availability SLO)"
          description: >
            The error rate has been above 3% for the last 6 hours and 30 minutes.
            At this rate the 30-day error budget will be exhausted in approximately 5 days.
            Current 6h error ratio: {{ $value | humanizePercentage }}.
```

---

### 5. Reload Prometheus to pick up the new rules

```powershell
# Send a POST to the Prometheus lifecycle endpoint to reload config without restart
Invoke-RestMethod -Method Post -Uri "http://localhost:9090/-/reload"
```

If the reload endpoint is not enabled, add `--web.enable-lifecycle` to the `command` section of the Prometheus service in `docker-compose.yml` (it was already included in TASK-10.29's compose snippet). Alternatively, restart the service:

```powershell
docker compose restart prometheus
```

Verify the recording rules loaded without errors:

```powershell
Invoke-RestMethod -Uri "http://localhost:9090/api/v1/rules" |
  Select-Object -ExpandProperty data | ConvertTo-Json -Depth 5
```

You should see the `instagram_sli_recording_rules` group and the `instagram-slo-burn-rate` group in the output.

---

### 6. Create the Grafana SLO dashboard

Create `docs/infra/grafana/dashboards/instagram-slo.json`:

```json
{
  "id": null,
  "uid": "instagram-slo",
  "title": "Instagram — SLO Dashboard",
  "tags": ["instagram", "slo"],
  "timezone": "browser",
  "schemaVersion": 38,
  "version": 1,
  "refresh": "1m",
  "panels": [
    {
      "id": 1,
      "title": "Current Availability SLI (5m window)",
      "type": "stat",
      "gridPos": { "x": 0, "y": 0, "w": 6, "h": 4 },
      "options": {
        "reduceOptions": { "calcs": ["lastNotNull"] },
        "thresholds": {
          "steps": [
            { "color": "red", "value": 0 },
            { "color": "yellow", "value": 0.99 },
            { "color": "green", "value": 0.995 }
          ]
        },
        "unit": "percentunit"
      },
      "targets": [
        {
          "expr": "1 - job:http_requests_errors:ratio_rate5m",
          "legendFormat": "Availability",
          "refId": "A"
        }
      ]
    },
    {
      "id": 2,
      "title": "Error Budget Remaining (30-day, feed SLO = 99.5%)",
      "type": "gauge",
      "gridPos": { "x": 6, "y": 0, "w": 6, "h": 4 },
      "options": {
        "reduceOptions": { "calcs": ["lastNotNull"] },
        "thresholds": {
          "steps": [
            { "color": "red", "value": 0 },
            { "color": "yellow", "value": 0.25 },
            { "color": "green", "value": 0.5 }
          ]
        },
        "unit": "percentunit",
        "min": 0,
        "max": 1
      },
      "targets": [
        {
          "expr": "job:error_budget_remaining:ratio",
          "legendFormat": "Budget remaining",
          "refId": "A"
        }
      ]
    },
    {
      "id": 3,
      "title": "Error Ratio Over Time (1h window)",
      "type": "timeseries",
      "gridPos": { "x": 0, "y": 4, "w": 12, "h": 8 },
      "targets": [
        {
          "expr": "job:http_requests_errors:ratio_rate1h",
          "legendFormat": "1h error ratio",
          "refId": "A"
        },
        {
          "expr": "0.005",
          "legendFormat": "SLO budget limit (0.5%)",
          "refId": "B"
        }
      ]
    },
    {
      "id": 4,
      "title": "Burn Rate (1h window vs SLO budget)",
      "type": "timeseries",
      "gridPos": { "x": 12, "y": 0, "w": 12, "h": 12 },
      "targets": [
        {
          "expr": "job:http_requests_errors:ratio_rate1h / 0.005",
          "legendFormat": "Burn rate (1h)",
          "refId": "A"
        },
        {
          "expr": "14.4",
          "legendFormat": "Fast-burn threshold (14.4x)",
          "refId": "B"
        },
        {
          "expr": "6",
          "legendFormat": "Slow-burn threshold (6x)",
          "refId": "C"
        }
      ]
    }
  ]
}
```

---

### 7. Restart Grafana to pick up the new dashboard

```powershell
docker compose restart grafana
```

Open `http://localhost:3000` → Dashboards → **Instagram — SLO Dashboard**. The stat panel showing current availability SLI should display a green value near `100%`.

---

## Checklist

- [ ] Define SLIs from `http_server_requests`: availability (non-5xx ratio) and latency (fraction under threshold) for the feed, profile, and login endpoints
- [ ] Document SLO targets, windows, and rationale in `docs/infra/slo.md`
- [ ] Add Prometheus recording rules computing the SLI ratios and the 30-day error budget
- [ ] Add multi-window, multi-burn-rate alert rules (fast burn: 1h + 5m; slow burn: 6h + 30m)
- [ ] Add a Grafana SLO panel: current SLI, target line, remaining error budget

---

## How to Verify

**1. Recording rules appear in Prometheus:**

Open `http://localhost:9090/graph` and run:

```
job:http_requests_errors:ratio_rate5m
```

The query should return a value (even `0` if no errors) — proving the recording rule is evaluated.

**2. Burn-rate alerts are loaded:**

Open `http://localhost:9090/alerts`. You should see `SLOFeedFastBurn` and `SLOFeedSlowBurn` listed as `inactive`.

**3. Grafana SLO dashboard renders:**

Open `http://localhost:3000` → Dashboards → Instagram — SLO Dashboard. The "Current Availability SLI" stat panel should show a value. If the backend has been running cleanly, it should be green (≥ 99.5%).

**4. Simulate a burn-rate alert (optional):**

Send a stream of requests to a path that returns 500. Temporarily modify `application-local.yml` to lower the threshold, or force errors another way. Wait for the 5m window to fill and watch the alert transition to `pending` then `firing` in the Prometheus UI.

```powershell
# Spam requests to an endpoint that will 500 (e.g., cause a DB error)
1..200 | ForEach-Object {
    try { Invoke-RestMethod -Uri "http://localhost:8080/api/v1/feed" `
          -Headers @{ Authorization = "Bearer $token" } } catch {}
}
```

After 2 minutes (the `for: 2m` clause), the `SLOFeedFastBurn` alert should move to `firing` if the 5xx rate was high enough.

---

## Notes / Gotchas

**"The error budget remaining gauge shows a negative value."**
A negative value means the SLO is already broken for the current 30-day window — more errors happened than the budget allows. This is expected when testing with a small request volume and deliberately forcing errors. In production, a negative gauge is a serious signal.

**"Recording rule returns `NaN`."**
`NaN` results from a 0/0 division. This happens when the denominator (`all requests`) is zero — i.e., no requests have been made in the rate window. Send at least one request and wait one evaluation interval (1 minute).

**"The 30-day error budget recording rule is inaccurate."**
The `increase()` function over 30 days requires Prometheus to retain 30 days of data. By default TASK-10.29 sets `--storage.tsdb.retention.time=7d`. Update it to `35d` in the Prometheus `command` in `docker-compose.yml`. Note that 30 days of retention requires approximately 1–5 GB of disk depending on request volume.

**"I want per-endpoint SLOs, not just a global availability SLI."**
Add a `uri` label to the recording rules by extending the `by (uri)` clause and adding a `uri` label filter. For example:

```yaml
- record: endpoint:feed:error_ratio_rate5m
  expr: |
    sum(rate(http_server_requests_seconds_count{
      application="instagram", uri="/api/v1/feed", status=~"5.."}[5m]))
    /
    sum(rate(http_server_requests_seconds_count{
      application="instagram", uri="/api/v1/feed"}[5m]))
```

Then create separate burn-rate alerts per `uri` label.

**References:**
- [Google SRE Workbook — Alerting on SLOs](https://sre.google/workbook/alerting-on-slos/)
- [Prometheus recording rules](https://prometheus.io/docs/prometheus/latest/configuration/recording_rules/)
- [Multi-burn-rate alerting](https://sre.google/workbook/alerting-on-slos/#6-multiwindow-multi-burn-rate-alerts)

**Cross-task references:**
- [TASK-10.27](TASK-10.27-actuator-micrometer.md) — the `http_server_requests_seconds` metric this task builds on
- [TASK-10.29](TASK-10.29-grafana-prometheus-alerting.md) — the Prometheus and Grafana setup required here

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **SLI vs SLO vs SLA** — indicators, objectives, agreements — https://sre.google/sre-book/service-level-objectives/
- **Implementing SLOs** — choosing good indicators and targets — https://sre.google/workbook/implementing-slos/
- **Error budgets & burn-rate alerts** — alert on how fast you're spending reliability — https://sre.google/workbook/alerting-on-slos/

### Official docs (code reference)
- **Google SRE Book (free)** — https://sre.google/sre-book/table-of-contents/
- **Prometheus recording rules** — https://prometheus.io/docs/prometheus/latest/configuration/recording_rules/
