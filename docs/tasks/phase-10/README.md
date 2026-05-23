# Phase 10 — Performance, Security & Polish

> **Depends on:** All previous phases (0–9)
> **BRD refs:** NFR-001 → NFR-013
> **Branch prefix:** `chore/phase-10-` or `fix/phase-10-`

This folder explodes the [Phase 10 overview](../phase-10-performance-security-polish.md) into one detailed,
beginner-friendly file per task. Each task file follows the same layout:
**Overview → Level → Why → Prerequisites → Files to Create / Modify → Step-by-Step → Checklist → How to Verify → Notes / Gotchas.**

Every task is tagged with a **Level**:

- **Warm-up** — a small, low-risk exercise that favours *understanding and measuring*. Do it first to build intuition.
- **Core** — the actual Phase 10 deliverables.
- **Stretch** — harder, senior-level work that builds on the core tasks.

---

## Task Index

### Performance

| Task | Title | Level | Status |
|------|-------|-------|--------|
| [TASK-10.1](TASK-10.1-performance-baseline-explain-analyze.md) | Establish a performance baseline with `EXPLAIN ANALYZE` | Warm-up | ⬜ |
| [TASK-10.2](TASK-10.2-hikaricp-pool-tuning.md) | Tune the HikariCP connection pool | Warm-up | ⬜ |
| [TASK-10.3](TASK-10.3-redis-caching.md) | Redis caching for feed & profiles | Core | ⬜ |
| [TASK-10.4](TASK-10.4-n-plus-1-query-review.md) | N+1 query review | Core | ⬜ |
| [TASK-10.5](TASK-10.5-cdn-media-urls.md) | CDN-backed media URLs | Core | ⬜ |
| [TASK-10.6](TASK-10.6-frontend-image-optimization.md) | Frontend image optimization | Core | ⬜ |
| [TASK-10.7](TASK-10.7-database-index-audit.md) | Database index audit & query-plan coverage | Core | ⬜ |
| [TASK-10.8](TASK-10.8-keyset-pagination.md) | Keyset (cursor) pagination for list endpoints | Core | ⬜ |
| [TASK-10.9](TASK-10.9-response-compression.md) | HTTP response compression & payload slimming | Core | ⬜ |
| [TASK-10.10](TASK-10.10-async-processing-virtual-threads.md) | Async processing with tuned executors + virtual threads | Stretch | ⬜ |
| [TASK-10.11](TASK-10.11-streaming-large-exports.md) | Streaming large exports (CSV/ZIP, no OOM) | Stretch | ⬜ |
| [TASK-10.12](TASK-10.12-chunked-resumable-upload.md) | Chunked / resumable large-file upload (up to 2 GB) | Stretch | ⬜ |
| [TASK-10.13](TASK-10.13-spring-batch-bulk-import.md) | Spring Batch: bulk import posts for a user | Stretch | ⬜ |

### Security

| Task | Title | Level | Status |
|------|-------|-------|--------|
| [TASK-10.14](TASK-10.14-security-headers.md) | Add baseline security headers | Warm-up | ⬜ |
| [TASK-10.15](TASK-10.15-secrets-hygiene.md) | Secrets hygiene check | Warm-up | ⬜ |
| [TASK-10.16](TASK-10.16-jwt-hardening.md) | JWT hardening | Core | ⬜ |
| [TASK-10.17](TASK-10.17-rate-limiting.md) | Rate limiting | Core | ⬜ |
| [TASK-10.18](TASK-10.18-input-validation-hardening.md) | Input validation hardening | Core | ⬜ |
| [TASK-10.19](TASK-10.19-owasp-dependency-check.md) | OWASP dependency check in CI | Core | ⬜ |
| [TASK-10.20](TASK-10.20-https-tls.md) | HTTPS / TLS | Core | ⬜ |
| [TASK-10.21](TASK-10.21-object-level-authorization-idor.md) | Object-level authorization & IDOR audit | Core | ⬜ |
| [TASK-10.22](TASK-10.22-media-upload-hardening.md) | Media upload hardening | Core | ⬜ |
| [TASK-10.23](TASK-10.23-dast-zap-scan.md) | DAST scan in CI (OWASP ZAP baseline) | Core | ⬜ |
| [TASK-10.24](TASK-10.24-idempotency-keys.md) | Idempotency keys for unsafe POSTs | Stretch | ⬜ |

### Observability

| Task | Title | Level | Status |
|------|-------|-------|--------|
| [TASK-10.25](TASK-10.25-trace-request-end-to-end.md) | Trace one request end-to-end | Warm-up | ⬜ |
| [TASK-10.26](TASK-10.26-structured-logging-mdc.md) | Structured logging & MDC | Core | ⬜ |
| [TASK-10.27](TASK-10.27-actuator-micrometer.md) | Actuator & Micrometer | Core | ⬜ |
| [TASK-10.28](TASK-10.28-distributed-tracing.md) | Distributed tracing | Core | ⬜ |
| [TASK-10.29](TASK-10.29-grafana-prometheus-alerting.md) | Grafana dashboards & Prometheus alerting | Core | ⬜ |
| [TASK-10.30](TASK-10.30-loki-log-aggregation.md) | Centralized log aggregation (Loki + Promtail) | Core | ⬜ |
| [TASK-10.31](TASK-10.31-sli-slo-error-budget.md) | SLIs, SLOs & error-budget burn-rate alerts | Core | ⬜ |
| [TASK-10.32](TASK-10.32-frontend-rum-sentry.md) | Frontend RUM & end-to-end error tracking (Sentry) | Core | ⬜ |

### Testing

| Task | Title | Level | Status |
|------|-------|-------|--------|
| [TASK-10.33](TASK-10.33-testcontainers-one-test.md) | Swap one integration test to Testcontainers | Warm-up | ⬜ |
| [TASK-10.34](TASK-10.34-cover-untested-branch.md) | Cover one untested branch | Warm-up | ⬜ |
| [TASK-10.35](TASK-10.35-coverage-gate.md) | Backend test coverage gate | Core | ⬜ |
| [TASK-10.36](TASK-10.36-frontend-component-tests.md) | Frontend component tests | Core | ⬜ |
| [TASK-10.37](TASK-10.37-e2e-playwright.md) | E2E smoke tests (Playwright) | Core | ⬜ |
| [TASK-10.38](TASK-10.38-archunit-fitness-tests.md) | Architecture fitness tests (ArchUnit) | Core | ⬜ |
| [TASK-10.39](TASK-10.39-testcontainers-integration-suite.md) | Testcontainers for the integration suite | Core | ⬜ |
| [TASK-10.40](TASK-10.40-k6-load-soak-testing.md) | Load & soak testing (k6) | Core | ⬜ |

### DevOps

| Task | Title | Level | Status |
|------|-------|-------|--------|
| [TASK-10.41](TASK-10.41-dockerignore.md) | Add `.dockerignore` files | Warm-up | ⬜ |
| [TASK-10.42](TASK-10.42-smoke-test-script.md) | Write a one-command smoke test script | Warm-up | ⬜ |
| [TASK-10.43](TASK-10.43-first-adr.md) | Write your first ADR (Architecture Decision Record) | Warm-up | ⬜ |
| [TASK-10.44](TASK-10.44-dockerfile-backend.md) | Dockerfile: backend | Core | ⬜ |
| [TASK-10.45](TASK-10.45-dockerfile-frontend.md) | Dockerfile: frontend | Core | ⬜ |
| [TASK-10.46](TASK-10.46-docker-compose.md) | Full docker-compose.yml | Core | ⬜ |
| [TASK-10.47](TASK-10.47-cicd-docker-build-push.md) | CI/CD: Docker build & push | Core | ⬜ |
| [TASK-10.48](TASK-10.48-shedlock-scheduled-jobs.md) | Scheduled jobs with distributed locking (ShedLock) | Stretch | ⬜ |

### Documentation & Operations

| Task | Title | Level | Status |
|------|-------|-------|--------|
| [TASK-10.49](TASK-10.49-troubleshooting-runbook.md) | Troubleshooting runbook for common failures | Warm-up | ⬜ |
| [TASK-10.50](TASK-10.50-architecture-diagrams-mermaid.md) | Architecture & flow diagrams (Mermaid) | Core | ⬜ |
| [TASK-10.51](TASK-10.51-oom-heap-dump-lab.md) | Diagnose an `OutOfMemoryError` from a heap dump | Warm-up | ⬜ |
| [TASK-10.52](TASK-10.52-logging-best-practices.md) | Logging best practices audit | Warm-up | ⬜ |

---

## Recommended Order

Work topic by topic, and within each topic do the **Warm-up** tasks first to build intuition, then **Core**, then **Stretch**.

```
Performance:    10.1 → 10.2  (warm-up)  →  10.3 … 10.9 (core)  →  10.10 … 10.13 (stretch)
Security:       10.14 → 10.15 (warm-up) →  10.16 … 10.23 (core) →  10.24 (stretch)
Observability:  10.25 (warm-up)         →  10.26 … 10.32 (core)
Testing:        10.33 → 10.34 (warm-up) →  10.35 … 10.40 (core)
DevOps:         10.41 → 10.42 → 10.43 (warm-up) → 10.44 … 10.47 (core) → 10.48 (stretch)
Docs & Ops:     10.49 → 10.51 → 10.52 (warm-up) → 10.50 (core)
```
