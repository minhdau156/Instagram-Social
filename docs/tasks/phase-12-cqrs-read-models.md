# Phase 12 — CQRS & Read Models

> **Track:** Backend · **Depends on:** Phase 11 · **New tools:** Postgres materialized views (optionally a separate read store)  
> **Branch prefix:** `feat/phase-12-`

---

> **Skills you'll build:**
> - Command/Query Responsibility Segregation
> - Building denormalized read models updated by Phase 11 events (projections)
> - Reasoning about eventual consistency and designing UX around it
> - Materialized views vs. event-built projections
>
> **Best practices:** read models are disposable and rebuildable from events; embrace eventual consistency with optimistic UI updates; don't share entities between the command and query sides.

---

> **How to read this file**
> - **Why:** the problem this task solves — read it before you start.
> - **Done when:** a concrete way to prove the task works. If you can't demonstrate it, it isn't finished.

---

## Design

### TASK-12.1 — Inventory read-heavy queries (feed, profile, post detail) and design read-model tables
> **Why:** You can't denormalize well until you know which reads are hot and what shape they need — this step turns guesses into a concrete table design.
> **Done when:** A short design note lists the top read queries with their current cost and a proposed read-model table per query, reviewed before any code.
- [ ] Identify the hot read paths (home feed, profile, post detail) and capture an `EXPLAIN ANALYZE` baseline for each
- [ ] For each, sketch a denormalized read-model table (columns and access pattern) in `docs/adr/` or a design note
- [ ] Decide projection (event-built) vs. materialized view per query and record the choice
- [ ] Confirm read and write sides will use separate tables/entities (no shared JPA entity)

### TASK-12.2 — Precomputed per-user feed read-model table
> **Why:** A pre-joined per-user feed table turns a multi-join feed query into a single indexed read.
> **Done when:** Flyway applies the migration and the table holds one denormalized row per (user, post) ready to serve a feed page in one query.
- [ ] Create `backend/src/main/resources/db/migration/V6__add_feed_read_model.sql` (`user_id`, `post_id`, denormalized author/media/count fields, `created_at`, index on `(user_id, created_at desc)`)
- [ ] Add `FeedReadModelJpaEntity.java` + `FeedReadModelJpaRepository.java` in `adapter/out/persistence/`
- [ ] Add a `FeedReadModelRepository` out-port in `domain/port/out/`
- [ ] Verify a paged read returns rows in correct order via a `@DataJpaTest`

---

## Projections

### TASK-12.3 — Projector consumers that update read models from events
> **Why:** Projectors keep read models in sync by reacting to the Phase 11 events, so the query side never queries the write tables.
> **Done when:** A new post / like / follow event updates the feed read model rows (verify the projected row appears/changes after the event).
- [ ] Add `adapter/in/messaging/FeedProjector.java` consuming `PostCreated`/`PostLiked`/`CommentAdded`/`UserFollowed`
- [ ] Upsert/maintain `FeedReadModel` rows (insert on new post, bump counters on like/comment)
- [ ] Reuse the Phase 11 dedupe table so projections are idempotent
- [ ] Add a test asserting the projection reflects a sequence of events exactly once each

### TASK-12.4 — Point `FeedController` at the read model
> **Why:** The whole point of CQRS is fast reads — the query API must serve from the read model, not the normalized write schema.
> **Done when:** `GET /api/v1/feed` returns data sourced from the feed read model and the request issues a single, constant-cost query.
- [ ] Add a `GetHomeFeedUseCase` query path (or query service) that reads via `FeedReadModelRepository`
- [ ] Update `FeedController` / `FeedService` query side to use the read model instead of the write tables
- [ ] Keep response DTOs unchanged so the frontend is unaffected
- [ ] Verify with Hibernate statistics that the feed read is a single query

---

## Operations

### TASK-12.5 — Read-model rebuild job (event replay)
> **Why:** Read models are disposable — being able to rebuild them from events lets you fix bugs or add fields without data loss.
> **Done when:** Running the rebuild job repopulates an emptied feed read-model table to match the source data.
- [ ] Add a rebuild endpoint or `@Scheduled`/CLI job in `adapter/in/` that replays from the source of truth (outbox/events or write tables)
- [ ] Truncate-and-rebuild safely (idempotent, resumable)
- [ ] Log progress and final row counts via SLF4J
- [ ] Verify: empty the read model, run rebuild, confirm the feed serves correctly again

### TASK-12.6 — Materialized view for trending hashtags + scheduled refresh
> **Why:** Trending is an aggregate read that's expensive to compute on demand; a materialized view caches it and a scheduled refresh keeps it fresh.
> **Done when:** Querying the trending endpoint reads from the materialized view and a scheduled task refreshes it on an interval.
- [ ] Create `backend/src/main/resources/db/migration/V7__trending_hashtags_mv.sql` defining a `MATERIALIZED VIEW` with a unique index for `REFRESH CONCURRENTLY`
- [ ] Add a `@Scheduled` job that runs `REFRESH MATERIALIZED VIEW CONCURRENTLY`
- [ ] Point the trending-hashtags query at the view
- [ ] Verify the view's data updates after the scheduled refresh runs

---

## Tests

### TASK-12.7 — Tests: command -> event -> projection -> query consistency
> **Why:** CQRS only works if the full pipeline converges; an end-to-end test proves the read side eventually matches the write side.
> **Done when:** An integration test issues a command, lets the event/projection flow run, and asserts the query side returns the expected (eventually consistent) result.
- [ ] Add an IT (Testcontainers Kafka + Postgres) covering create-post -> `PostCreated` -> projector -> feed query
- [ ] Assert eventual consistency with an `Awaitility`-style poll, not a fixed sleep
- [ ] Cover a like/comment counter update propagating to the read model
- [ ] Add a rebuild test asserting a rebuilt read model matches a freshly projected one
