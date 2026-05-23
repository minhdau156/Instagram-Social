# Phase 16 — SRE: SLOs, Alerting & Progressive Delivery

> **Track:** Cloud/SRE · **Depends on:** Phases 10, 14 · **New tools:** Prometheus Alertmanager, Grafana, Argo Rollouts / Flagger, k6  
> **Branch prefix:** `chore/phase-16-`

---

> **Skills you'll build:**
> - SLIs / SLOs / error budgets
> - Alerting on symptoms (user pain), not causes
> - Dashboards that tell a story (RED / USE methods)
> - Canary & blue-green deployments with automatic rollback
>
> **Best practices:** alert on user-facing SLOs (latency, error rate); page only on actionable alerts; treat every deploy as a canary; automate rollback on SLO breach.

---

> **How to read this file**
> - **Why:** the problem this task solves — read it before you start.
> - **Done when:** a concrete way to prove the task works. If you can't demonstrate it, it isn't finished.

---

## Define & Alert

### TASK-16.1 — Define SLIs/SLOs for feed latency, error rate, availability
> **Why:** "Healthy" has to be a number before you can alert on it; SLOs turn vague reliability goals into measurable targets backed by an error budget.
> **Done when:** A written SLO doc lists target numbers and the exact PromQL that measures each SLI against the Phase 10 `/actuator/prometheus` metrics.
- [ ] Pick SLIs: feed p95 latency, HTTP 5xx error rate, availability (good requests / total)
- [ ] Set SLO targets and the rolling window (e.g. p95 < 300ms, error rate < 1%, 99.9% over 30d)
- [ ] Write the PromQL for each SLI using `http_server_requests_seconds` metrics from Actuator
- [ ] Compute the error budget and document it in `docs/sre/slo.md`

### TASK-16.2 — Prometheus recording + alert rules
> **Why:** Recording rules precompute expensive SLI queries, and alert rules fire when an SLO is at risk — so you find out before users complain.
> **Done when:** Burning through the error budget faster than allowed makes an alert show as `firing` in the Prometheus UI.
- [ ] Add `prometheus/recording.rules.yml` precomputing the SLI rates from TASK-16.1
- [ ] Add `prometheus/alert.rules.yml` with multi-window error-budget burn-rate alerts
- [ ] Confirm Prometheus is scraping `/actuator/prometheus` from all backend pods
- [ ] Trigger an alert (inject errors or latency) and watch it move to `firing`

### TASK-16.3 — Alertmanager routing (Slack/email) + runbooks
> **Why:** An alert nobody sees is useless; routing delivers it to the right channel, and a runbook tells the on-call exactly what to do next.
> **Done when:** A firing alert posts to your Slack/email channel with a link to a runbook describing the first response steps.
- [ ] Configure `alertmanager.yml` with a receiver (Slack webhook or email) and routing tree
- [ ] Group/throttle alerts and set severity-based routing (page vs. notify)
- [ ] Write a runbook per alert under `docs/sre/runbooks/` and link it via the alert annotation
- [ ] Send a test alert and confirm it lands with the runbook link

### TASK-16.4 — Grafana dashboards (RED/USE)
> **Why:** Dashboards turn raw metrics into a story — RED (Rate, Errors, Duration) for request health and USE (Utilization, Saturation, Errors) for resources — so you can diagnose at a glance.
> **Done when:** A provisioned Grafana dashboard shows live RED metrics for the feed endpoint and USE metrics for the pods, plus the SLO/error-budget status.
- [ ] Add Prometheus as a Grafana datasource (provisioned as code)
- [ ] Build a RED dashboard JSON for the API (request rate, error %, latency percentiles)
- [ ] Build a USE panel set for CPU/memory utilization and saturation per pod
- [ ] Add an SLO panel showing remaining error budget; store dashboards in `grafana/dashboards/`

---

## Ship Safely

### TASK-16.5 — Canary rollout with Argo Rollouts / Flagger
> **Why:** A canary sends a small slice of traffic to the new version first, so a bad deploy hurts 5% of users for a minute instead of everyone.
> **Done when:** Deploying a new image shifts traffic in steps (e.g. 5% → 25% → 100%) and you can watch the canary progress through its analysis.
- [ ] Install Argo Rollouts (or Flagger) into the cluster
- [ ] Convert the backend Deployment in the Helm chart to a `Rollout` with a canary strategy
- [ ] Define traffic-shift steps with pauses between increments
- [ ] Deploy a new image and watch the canary advance via the rollouts dashboard/CLI

### TASK-16.6 — Automated rollback on SLO breach
> **Why:** A canary is only safe if it can abort itself; tying the analysis to your SLO metrics means a bad release rolls back automatically without a human in the loop.
> **Done when:** A deliberately broken image (high error rate) causes the canary analysis to fail and traffic returns to the previous version automatically.
- [ ] Add an `AnalysisTemplate` querying the TASK-16.2 error-rate/latency metrics from Prometheus
- [ ] Set failure thresholds that abort the rollout when the SLI exceeds the SLO
- [ ] Deploy a known-bad image and confirm automatic rollback to the stable version
- [ ] Verify an alert fires and the runbook documents the auto-rollback behavior

### TASK-16.7 — k6 load test to validate the SLOs
> **Why:** You should prove the system actually meets its SLOs under realistic load before trusting them in prod — and have a repeatable test to re-run after changes.
> **Done when:** A k6 script drives load against the feed/login endpoints and its thresholds (matching the SLOs) pass, with results visible in Grafana.
- [ ] Write `k6/feed-load.js` exercising login + `GET /api/v1/feed` at a target request rate
- [ ] Encode the SLO targets as k6 `thresholds` (p95 latency, error rate) so the run fails on breach
- [ ] Run against the staging cluster and watch the RED dashboard during the test
- [ ] Save the summary to `docs/sre/load-test-results.md` and (optional) add a k6 step to CI
