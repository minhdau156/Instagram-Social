# Phase 18 — Recommendations & Ranking Feed

> **Track:** Product · **Depends on:** Phases 5, 11 · **New tools:** an analytics event pipeline; optionally a lightweight ranking model  
> **Branch prefix:** `feat/phase-18-`

---

> **Skills you'll build:**
> - Candidate generation → scoring → ranking pipeline
> - Feature engineering from Phase 11 events
> - A/B testing and product metrics
> - Analytics event design
>
> **Best practices:** log raw events first (you can't model what you didn't capture); separate candidate generation from ranking; always A/B test ranking changes; watch for engagement feedback loops.

---

> **How to read this file**
> - **Why:** the problem this task solves — read it before you start.
> - **Done when:** a concrete way to prove the task works. If you can't demonstrate it, it isn't finished.

---

## Backend

### TASK-18.1 — Analytics event schema + ingestion (impressions, dwell, engagement)
> **Why:** You can only rank on signals you have captured, so raw impression / dwell / engagement events must be logged before any modeling begins.
> **Done when:** Scrolling the feed in the browser produces rows in `analytics_events` with `event_type`, `post_id`, `user_id`, and `dwell_ms`.
- [ ] Add Flyway migration for `analytics_events` (`id`, `user_id`, `post_id`, `event_type`, `dwell_ms`, `created_at`) with an index on `(user_id, created_at)`
- [ ] Add `AnalyticsEvent` domain model + `AnalyticsEventType` enum and an `AnalyticsEventRepository` out-port
- [ ] Add `RecordAnalyticsEventUseCase` in-port and a batch `POST /api/v1/analytics/events` controller + request DTO
- [ ] Frontend: add `src/api/analyticsApi.ts` and a `src/hooks/analytics/useImpressionTracker.ts` (`IntersectionObserver` + dwell timing) wired into `PostCard`

### TASK-18.2 — Candidate generation (following + interests + trending)
> **Why:** Ranking can't score every post in the database, so a cheap first stage must produce a bounded candidate set from several sources.
> **Done when:** `GET /api/v1/feed/candidates` returns a deduplicated list blended from followed authors, the user's interests, and trending posts.
- [ ] Add `CandidateGeneratorUseCase` in-port returning `List<UUID>` post ids with their source tag
- [ ] Implement `CandidateGenerationService` combining a following query, the existing `user_interests` data, and trending hashtags from Phase 5
- [ ] Add a `FeedCandidateQueryAdapter` (`adapter/out/persistence/`) with the union/dedupe query
- [ ] Cap and deduplicate candidates (e.g. top 500) before they reach the scorer

### TASK-18.3 — Precomputed engagement features (a simple feature store)
> **Why:** Scoring needs per-post and per-user features fast, so they should be precomputed from analytics events rather than aggregated at request time.
> **Done when:** A scheduled rollup populates `post_features` (e.g. CTR, recent like velocity) and a feed request reads them without scanning `analytics_events`.
- [ ] Add Flyway migration for `post_features` (`post_id`, `impressions`, `engagements`, `ctr`, `like_velocity`, `updated_at`)
- [ ] Add a `@Scheduled` `FeatureRollupJob` in `infrastructure/` aggregating `analytics_events` into `post_features`
- [ ] Add a `FeatureStore` out-port + adapter exposing `getFeatures(postIds)` for the scorer

### TASK-18.4 — Ranking scorer (heuristic first, optional ML model later)
> **Why:** A transparent, tunable heuristic proves the pipeline end-to-end before any ML, and gives a baseline to A/B against.
> **Done when:** The ranked feed orders candidates by a documented score (recency + engagement features) and re-ordering is visible versus the chronological feed.
- [ ] Add a `FeedRanker` in-port with a `RankedPost(postId, score)` record
- [ ] Implement `HeuristicFeedRanker` in `application/usecase/` combining recency decay and `post_features` signals into a score
- [ ] Wire candidate generation → feature lookup → ranking into a `GetRankedFeedUseCase` and add `GET /api/v1/feed/ranked`
- [ ] Document the scoring formula in a comment / `docs/` note so weights are tunable

### TASK-18.5 — A/B test assignment + framework
> **Why:** Ranking changes must be measured against a control, so users need stable, deterministic assignment to experiment variants.
> **Done when:** A given user is consistently bucketed into the same variant across requests, and the chosen feed strategy follows their bucket.
- [ ] Add Flyway migration for `experiments` / `experiment_assignments` (or assign deterministically by hashing `user_id`)
- [ ] Add an `ExperimentAssignmentService` returning `control` (chronological) vs `treatment` (ranked) for a user
- [ ] Branch `FeedController` between the chronological feed and `GetRankedFeedUseCase` based on assignment, and stamp the variant on analytics events

### TASK-18.6 — Metrics dashboard (CTR, session length)
> **Why:** An experiment is meaningless without a way to compare its product metrics, so per-variant CTR and session length must be queryable.
> **Done when:** A query (or `/actuator/prometheus` gauge) reports CTR and average session length split by experiment variant.
- [ ] Add a SQL view or scheduled aggregate computing per-variant CTR and session length from `analytics_events`
- [ ] Expose the metrics via Micrometer gauges or a `GET /api/v1/analytics/metrics` admin endpoint
- [ ] Frontend: add `src/pages/admin/RankingMetricsPage.tsx` charting variant comparison (admin-only route)

### TASK-18.7 — Offline evaluation harness
> **Why:** Replaying logged events lets you score a ranking change before exposing real users, catching regressions cheaply.
> **Done when:** Running the harness over a window of `analytics_events` prints a ranking quality metric (e.g. NDCG / precision@k) for a candidate ranker.
- [ ] Add an `OfflineEvalRunner` (a `CommandLineRunner` or test-scope utility) that loads historical events
- [ ] Compute a ranking metric (precision@k or NDCG) comparing the ranker's order against observed engagement
- [ ] Add a unit/integration test asserting the heuristic ranker beats the chronological baseline on the sample dataset
