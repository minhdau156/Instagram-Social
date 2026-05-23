# Advanced Track — Phases 11–20

> **Continues from Phase 10.** These phases take the project from "a complete app" to "a production-grade system," and intentionally stretch you into senior-level topics.
>
> This file is a **roadmap of outlines** — each phase lists its **Goal**, the **Skills you'll build**, the **Best practices** it teaches, and a **high-level task outline**. It is *not* fully expanded yet.
>
> 👉 **Next step:** pick any phase and ask me to *"expand Phase N into a full task file"* — I'll write it out as `phase-N-*.md` with a `**Why:**` / `**Done when:**` on every task, exactly like Phase 10.

---

## Tracks & suggested order

| Track | Phases | Theme |
|---|---|---|
| 🏛️ Backend architecture | 11 · 12 · 13 | Decouple, separate read/write, survive failure |
| ☁️ Cloud-native & SRE | 14 · 15 · 16 | Ship and run it in the cloud, reliably |
| ✨ Product features | 17 · 18 | Grow the app: Stories/Reels, ranked feed |
| 🔌 API & frontend depth | 19 · 20 | GraphQL, accessibility, performance |

```
Phase 10 ─┬─► 11 ─► 12 ─► 13        (backend architecture — do in order)
          ├─► 14 ─► 15 ─► 16        (cloud/SRE — 14 needs Phase 10 images)
          ├─► 17 ─► 18              (product — 18 is richer after 11)
          └─► 19 ─► 20              (API/frontend — anytime after Phase 10)
```

**Recommended path:** 11 → 12 → 13 first (they reshape the backend), then 14 → 16 to run it well. Product (17–18) and frontend (19–20) can slot in whenever you want a change of pace.

---

# 🏛️ Backend architecture

## Phase 11 — Event-Driven Architecture & Async Messaging
> **Track:** Backend · **Depends on:** Phases 4, 7 · **New tools:** Apache Kafka (or Redpanda), Spring Kafka, Testcontainers-Kafka

**Goal:** Decouple side-effects (notifications, feed fan-out, analytics) from the request path by publishing **domain events** to a message broker instead of calling other services inline.

**Skills you'll build:**
- Designing domain events vs. integration events
- The **transactional outbox** pattern — no lost events when the DB commit succeeds but the broker publish fails
- Idempotent consumers under at-least-once delivery
- Async fan-out, consumer groups, and backpressure

**Best practices:** events are immutable past-tense facts (`PostLiked`, not `LikePost`); version your event schema from day one; consumers must be idempotent; never publish to a broker *inside* a DB transaction — use the outbox.

**Task outline:**
1. TASK-11.1 — Add Kafka (or Redpanda) to `docker-compose.yml`
2. TASK-11.2 — Define domain event records (`PostCreated`, `PostLiked`, `UserFollowed`, `CommentAdded`)
3. TASK-11.3 — `outbox` table + Flyway migration
4. TASK-11.4 — Outbox relay (poller) that publishes committed events to Kafka
5. TASK-11.5 — Replace the `@EventListener` notification handler with a Kafka consumer
6. TASK-11.6 — Idempotency: dedupe consumed events by event id
7. TASK-11.7 — Feed fan-out-on-write consumer
8. TASK-11.8 — Integration tests with Testcontainers-Kafka

## Phase 12 — CQRS & Read Models
> **Track:** Backend · **Depends on:** Phase 11 · **New tools:** Postgres materialized views (optionally a separate read store)

**Goal:** Separate the **write model** (commands, normalized) from **read models** (queries, denormalized) so feed/profile reads are fast and decoupled from the write schema.

**Skills you'll build:**
- Command/Query Responsibility Segregation
- Building denormalized read models updated by Phase 11 events (projections)
- Reasoning about **eventual consistency** and designing UX around it
- Materialized views vs. event-built projections

**Best practices:** read models are disposable and rebuildable from events; embrace eventual consistency with optimistic UI updates; don't share entities between the command and query sides.

**Task outline:**
1. TASK-12.1 — Inventory read-heavy queries (feed, profile, post detail) and design read-model tables
2. TASK-12.2 — Precomputed per-user feed read-model table
3. TASK-12.3 — Projector consumers that update read models from events
4. TASK-12.4 — Point `FeedController` at the read model
5. TASK-12.5 — Read-model rebuild job (event replay)
6. TASK-12.6 — Materialized view for trending hashtags + scheduled refresh
7. TASK-12.7 — Tests: command → event → projection → query consistency

## Phase 13 — Resilience & Fault Tolerance
> **Track:** Backend · **Depends on:** Phases 6, 10, 11 · **New tools:** Resilience4j, Spring Cloud Circuit Breaker

**Goal:** Keep the app responsive when a dependency (DB, Redis, Kafka, MinIO, OAuth provider) is slow or down.

**Skills you'll build:**
- Timeouts, retries with exponential backoff + jitter
- Circuit breakers and half-open recovery
- Bulkheads (isolating thread pools so one slow dependency can't sink everything)
- Graceful degradation & fallbacks
- Basic chaos testing

**Best practices:** every network call gets a timeout; retry only idempotent operations; fail fast with a fallback rather than hanging; reflect degraded dependencies in `/actuator/health`.

**Task outline:**
1. TASK-13.1 — Add Resilience4j + actuator integration
2. TASK-13.2 — Timeouts on all outbound calls (MinIO, OAuth, Kafka)
3. TASK-13.3 — Circuit breaker around media storage, with a fallback
4. TASK-13.4 — Retry with backoff on transient DB/broker errors
5. TASK-13.5 — Bulkhead isolating the search/feed thread pools
6. TASK-13.6 — Cache-based feed fallback when the DB is degraded
7. TASK-13.7 — Chaos test: kill Redis/MinIO mid-test, assert graceful degradation
8. TASK-13.8 — Expose circuit-breaker state to Prometheus

---

# ☁️ Cloud-native & SRE

## Phase 14 — Kubernetes Deployment
> **Track:** Cloud/SRE · **Depends on:** Phase 10 (Docker images) · **New tools:** Kubernetes, Helm, kind/minikube

**Goal:** Run the whole stack on Kubernetes with health-based scheduling, autoscaling, and config separated from images.

**Skills you'll build:**
- Deployments, Services, Ingress
- ConfigMaps & Secrets
- Liveness / readiness / startup probes
- Horizontal Pod Autoscaler
- Helm templating & values per environment

**Best practices:** 12-factor config via env vars; one process per container; set resource requests/limits; never bake secrets into images; gate traffic behind readiness probes.

**Task outline:**
1. TASK-14.1 — Local cluster (kind/minikube) + namespace
2. TASK-14.2 — Deployment + Service manifests for backend & frontend
3. TASK-14.3 — Postgres/Redis/MinIO for dev (StatefulSets or operators)
4. TASK-14.4 — ConfigMap + Secret for app config
5. TASK-14.5 — Liveness/readiness probes wired to `/actuator/health`
6. TASK-14.6 — Ingress + TLS
7. TASK-14.7 — HPA on CPU / a custom metric
8. TASK-14.8 — Package it all as a Helm chart with per-env values

## Phase 15 — Infrastructure as Code (Terraform)
> **Track:** Cloud/SRE · **Depends on:** Phase 14 · **New tools:** Terraform, a cloud account (AWS/GCP/Azure)

**Goal:** Provision the cloud environment (network, managed DB/cache, object storage, registry, cluster) reproducibly from code.

**Skills you'll build:**
- Providers, resources, modules, variables, outputs
- Remote state + locking
- Multiple environments (dev/staging/prod)
- The `plan`/`apply` workflow and drift detection

**Best practices:** never click in the console for prod; keep state in a remote backend with locking; build reusable modules; least-privilege IAM; tag every resource.

**Task outline:**
1. TASK-15.1 — Terraform skeleton + remote state backend
2. TASK-15.2 — Network module (VPC/subnets)
3. TASK-15.3 — Managed Postgres + Redis modules
4. TASK-15.4 — Object storage (S3) + CDN
5. TASK-15.5 — Container registry + Kubernetes cluster module
6. TASK-15.6 — Deploy the Phase 14 Helm release (Terraform or GitOps handoff)
7. TASK-15.7 — Per-environment configs + `terraform plan` in CI

## Phase 16 — SRE: SLOs, Alerting & Progressive Delivery
> **Track:** Cloud/SRE · **Depends on:** Phases 10, 14 · **New tools:** Prometheus Alertmanager, Grafana, Argo Rollouts / Flagger, k6

**Goal:** Define what "healthy" means numerically, alert on it, and ship changes safely with automated canaries.

**Skills you'll build:**
- SLIs / SLOs / error budgets
- Alerting on symptoms (user pain), not causes
- Dashboards that tell a story (RED / USE methods)
- Canary & blue-green deployments with automatic rollback

**Best practices:** alert on user-facing SLOs (latency, error rate); page only on actionable alerts; treat every deploy as a canary; automate rollback on SLO breach.

**Task outline:**
1. TASK-16.1 — Define SLIs/SLOs for feed latency, error rate, availability
2. TASK-16.2 — Prometheus recording + alert rules
3. TASK-16.3 — Alertmanager routing (Slack/email) + runbooks
4. TASK-16.4 — Grafana dashboards (RED/USE)
5. TASK-16.5 — Canary rollout with Argo Rollouts / Flagger
6. TASK-16.6 — Automated rollback on SLO breach
7. TASK-16.7 — k6 load test to validate the SLOs

---

# ✨ Product features

## Phase 17 — Stories & Reels
> **Track:** Product · **Depends on:** Phases 2, 5 · **New tools:** FFmpeg (transcoding), a scheduled expiry job

**Goal:** Add ephemeral **Stories** (24h) and short-form video **Reels** with an async transcoding pipeline.

**Skills you'll build:**
- Modeling time-bounded (TTL) content
- Video upload → transcode → multiple renditions (HLS)
- View/"seen" tracking at scale
- Background job processing

**Best practices:** transcode asynchronously (never block the upload request); store renditions + a manifest; expire via a scheduled job *and* a read-time filter; track views idempotently.

**Task outline:**
1. TASK-17.1 — `stories` table + 24h expiry (migration)
2. TASK-17.2 — Story create/upload via presigned media
3. TASK-17.3 — Story viewer + "seen" tracking
4. TASK-17.4 — Expiry job + read-time filter
5. TASK-17.5 — `reels` model + video transcoding worker (FFmpeg → HLS)
6. TASK-17.6 — Reels feed (vertical, autoplay) on the frontend
7. TASK-17.7 — Tests + view-count accuracy

## Phase 18 — Recommendations & Ranking Feed
> **Track:** Product · **Depends on:** Phases 5, 11 · **New tools:** an analytics event pipeline; optionally a lightweight ranking model

**Goal:** Move from a chronological feed to a **ranked** feed driven by engagement signals, with A/B testing and analytics.

**Skills you'll build:**
- Candidate generation → scoring → ranking pipeline
- Feature engineering from Phase 11 events
- A/B testing and product metrics
- Analytics event design

**Best practices:** log raw events first (you can't model what you didn't capture); separate candidate generation from ranking; always A/B test ranking changes; watch for engagement feedback loops.

**Task outline:**
1. TASK-18.1 — Analytics event schema + ingestion (impressions, dwell, engagement)
2. TASK-18.2 — Candidate generation (following + interests + trending)
3. TASK-18.3 — Precomputed engagement features (a simple feature store)
4. TASK-18.4 — Ranking scorer (heuristic first, optional ML model later)
5. TASK-18.5 — A/B test assignment + framework
6. TASK-18.6 — Metrics dashboard (CTR, session length)
7. TASK-18.7 — Offline evaluation harness

---

# 🔌 API & frontend depth

## Phase 19 — GraphQL API Layer
> **Track:** API/Frontend · **Depends on:** Phases 2–5 · **New tools:** Spring for GraphQL, Apollo Client (frontend)

**Goal:** Offer a GraphQL API alongside REST so clients fetch exactly the data they need in one round trip — and learn to do it without N+1 query explosions.

**Skills you'll build:**
- Schema-first GraphQL design
- Resolvers + **DataLoader** (batching to kill N+1)
- Relay-style cursor pagination
- Auth inside GraphQL
- When *not* to use GraphQL (vs. REST/gRPC)

**Best practices:** schema-first; batch with DataLoader; enforce query depth/complexity limits to prevent abuse; use persisted queries in production; keep REST for binary/file uploads.

**Task outline:**
1. TASK-19.1 — Add Spring for GraphQL + a `.graphqls` schema
2. TASK-19.2 — Query resolvers for user / post / feed
3. TASK-19.3 — DataLoader batching for author / media / counts
4. TASK-19.4 — Relay-style cursor pagination
5. TASK-19.5 — Mutations (createPost, like) + auth
6. TASK-19.6 — Query depth/complexity limits
7. TASK-19.7 — Frontend: Apollo Client + migrate one page
8. TASK-19.8 — Tests + schema snapshot

## Phase 20 — Frontend Excellence: a11y, i18n, PWA & Performance
> **Track:** API/Frontend · **Depends on:** all frontend phases · **New tools:** react-i18next, vite-plugin-pwa/Workbox, axe, Lighthouse

**Goal:** Make the frontend usable by everyone, in any language, installable, and fast.

**Skills you'll build:**
- WCAG accessibility (keyboard nav, ARIA, focus management, contrast)
- Internationalization & localization
- PWA / offline-first (service worker, caching strategies)
- Core Web Vitals & React performance (memoization, list virtualization, code-splitting)

**Best practices:** semantic HTML first, ARIA second; externalize every user-facing string; test with a screen reader and keyboard only; measure with Lighthouse and set performance budgets; virtualize long lists.

**Task outline:**
1. TASK-20.1 — Accessibility audit (axe) + fix critical issues
2. TASK-20.2 — Keyboard navigation + focus management on modals/menus
3. TASK-20.3 — i18n setup with react-i18next + string extraction
4. TASK-20.4 — Locale switching + date/number formatting
5. TASK-20.5 — PWA: manifest + service worker (offline shell)
6. TASK-20.6 — Offline feed caching (stale-while-revalidate)
7. TASK-20.7 — Virtualize the feed/long lists (e.g. react-virtuoso)
8. TASK-20.8 — Lighthouse budget enforced in CI
