# TASK-10.7 — Database index audit & query-plan coverage

## Overview

The initial schema migration (`V1__initial_schema.sql`) creates indexes for the most obvious columns, but as the application grew through nine phases, several new query patterns emerged — follow-graph joins, notification reads, search queries, cursor-based pagination — without matching indexes. This task audits the hot queries with `EXPLAIN ANALYZE`, identifies which ones do sequential scans, and adds a Flyway migration to create the missing indexes. It also adds a partial index on `posts (deleted_at IS NULL)` to speed up the soft-delete filter that appears in almost every post query.

---

## Level

Core · Pairs with [TASK-10.1 baseline](TASK-10.1-performance-baseline-explain-analyze.md) / [TASK-10.3 caching](TASK-10.3-redis-caching.md) / [TASK-10.8 keyset pagination](TASK-10.8-keyset-pagination.md)

---

## Why

The fastest cache is a query that was already fast. Foreign keys, sort columns, and `WHERE` filters with no backing index force Postgres into sequential scans that get slower as tables grow — caching (TASK-10.3) only hides this problem, it does not fix it. A sequential scan on `notifications WHERE recipient_id = $1` reads every notification row in the table on every request; an index scan reads only the rows for that user. Adding the right composite indexes is typically a one-line Flyway migration with no application code change required.

---

## Prerequisites

- TASK-10.1 complete — you already have `EXPLAIN ANALYZE` output showing which scans are sequential.
- TASK-10.3 complete (optional but useful) — Redis caching means index improvements only matter on cache misses, which is still important.
- A `psql` or GUI client connected to the local `instagram` database.
- Understand what Flyway migration files are and where they live: `backend/src/main/resources/db/migration/`. The current highest migration is `V3__add_fts_indexes.sql`. Your new migration will be `V4__add_performance_indexes.sql`.

**Concepts to skim:**
- B-tree index: the default PostgreSQL index type. Ideal for equality (`=`), range (`<`, `>`, `BETWEEN`), and sort operations (`ORDER BY`).
- Composite index: an index on multiple columns — `(user_id, created_at DESC)`. Useful when queries filter on both columns together.
- Partial index: an index with a `WHERE` clause — `WHERE deleted_at IS NULL`. The index only includes rows matching the condition, making it smaller and faster for queries that also include that condition.
- Covering index: an index that includes all columns needed by a query so Postgres can answer entirely from the index without reading the table rows ("index-only scan").
- `EXPLAIN (ANALYZE, BUFFERS)`: runs the query and shows the real plan with timing and buffer hit/miss counts.

---

## Files to Create / Modify

```
backend/src/main/resources/db/migration/V4__add_performance_indexes.sql    (new)
docs/infra/query-baseline.md                                                 (modify — add "after" numbers)
```

---

## Step-by-Step

### 1. Audit the hot queries

Connect to the database:

```powershell
docker exec -it instagram-social-postgres-1 psql -U instagram -d instagram
```

Run `EXPLAIN (ANALYZE, BUFFERS)` for each query listed below. For each one, note:
- Does the plan show `Seq Scan` or `Index Scan`?
- What is `Execution Time: X ms`?

**Query A — home-feed (already done in TASK-10.1, re-confirm here):**

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.* FROM posts p
JOIN follows f ON f.following_id = p.user_id
WHERE f.follower_id = '<user_id>'::uuid
  AND f.is_approved = TRUE
  AND p.deleted_at IS NULL
ORDER BY p.created_at DESC
LIMIT 20;
```

**Query B — follow graph (followers list):**

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT f.* FROM follows f
WHERE f.following_id = '<user_id>'::uuid
  AND f.is_approved = TRUE
ORDER BY f.created_at DESC
LIMIT 20;
```

**Query C — notifications for a user:**

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT n.* FROM notifications n
WHERE n.recipient_id = '<user_id>'::uuid
ORDER BY n.created_at DESC
LIMIT 20;
```

**Query D — search users (if FTS not active):**

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT u.* FROM users u
WHERE u.username ILIKE 'test%'
ORDER BY u.follower_count DESC
LIMIT 10;
```

**Query E — comments for a post:**

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.* FROM comments c
WHERE c.post_id = '<post_id>'::uuid
  AND NOT c.is_deleted
ORDER BY c.created_at ASC
LIMIT 50;
```

For each query, record in `docs/infra/query-baseline.md` (from TASK-10.1) which scan type the planner chose and the execution time.

---

### 2. Identify missing indexes

Cross-reference the existing indexes in `V1__initial_schema.sql` with what the queries actually need:

**Already indexed (check before adding duplicates):**
- `idx_follows_following` on `follows (following_id)` — Query B uses this.
- `idx_follows_follower` on `follows (follower_id)` — home-feed uses this.
- `idx_notifications_recipient` on `notifications (recipient_id, created_at DESC)` — Query C uses this.
- `idx_comments_post` on `comments (post_id, created_at DESC) WHERE NOT is_deleted` — Query E uses this.
- `idx_posts_user` on `posts (user_id, created_at DESC)` — profile page uses this.

**Possibly missing or needing improvement:**

Check whether a composite index on `follows (following_id, is_approved)` would help Query B avoid a filter step. Run:

```sql
SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE tablename IN ('follows', 'posts', 'notifications', 'comments', 'user_stats')
ORDER BY tablename, indexname;
```

Compare the existing index list with what the query plans show.

Common gaps after Phase 1–9:
- `posts (user_id, created_at DESC) WHERE deleted_at IS NULL` — the existing `idx_posts_user` does not include the `deleted_at` filter; a partial index is more selective.
- `follows (following_id, is_approved, created_at DESC)` — composite for follower-count queries with approval filter.
- `user_stats (follower_count DESC)` — for "suggested users" ordering by follower count.

---

### 3. Create the Flyway migration

Create the file `backend/src/main/resources/db/migration/V4__add_performance_indexes.sql`:

```sql
-- =============================================================================
-- V4 — Performance Indexes
-- Phase 10 index audit: add missing indexes identified by EXPLAIN ANALYZE.
-- All indexes are CONCURRENT where possible to avoid locking in production;
-- Flyway runs migrations in a transaction, so CREATE INDEX CONCURRENTLY
-- cannot be used inside a migration (Postgres restriction). Use plain
-- CREATE INDEX IF NOT EXISTS instead.
-- =============================================================================

-- Partial index on posts for the deleted_at IS NULL filter that appears on
-- every post query. Narrower than the full-table index; Postgres can use it
-- whenever the WHERE clause includes `deleted_at IS NULL`.
CREATE INDEX IF NOT EXISTS idx_posts_not_deleted
    ON posts (user_id, created_at DESC)
    WHERE deleted_at IS NULL;

-- Composite index for cursor-based pagination on posts.
-- The keyset query uses: WHERE (created_at, id) < (:ts, :id) ORDER BY created_at DESC, id DESC.
-- This index covers both the filter and the sort.
CREATE INDEX IF NOT EXISTS idx_posts_cursor
    ON posts (created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

-- Follows: composite index to accelerate the follower-list query with
-- approval filter and creation-date sort.
CREATE INDEX IF NOT EXISTS idx_follows_following_approved
    ON follows (following_id, is_approved, created_at DESC);

-- user_stats: index for "suggested users" sorting by follower count.
CREATE INDEX IF NOT EXISTS idx_user_stats_followers
    ON user_stats (follower_count DESC);

-- search_history: already indexed on (user_id, searched_at DESC) in V1.
-- No additional index needed here.

-- =============================================================================
-- Indexes we considered and rejected:
-- - idx_posts_caption_gin on posts (caption gin_trgm_ops): already added by V3.
-- - idx_follows_follower_following: the composite PK on (follower_id, following_id)
--   already serves equality lookups; an additional index would duplicate storage.
-- - idx_notifications_all_columns: a wide covering index would help read speed but
--   hurt write throughput on every notification insert; rejected in favour of the
--   existing (recipient_id, created_at DESC) which is sufficient.
-- =============================================================================
```

---

### 4. Apply the migration

The migration runs automatically when the application starts (Flyway detects new migration files). To apply it manually without starting the full app:

```powershell
cd backend
mvn flyway:migrate -Dflyway.url=jdbc:postgresql://localhost:5432/instagram `
    -Dflyway.user=instagram -Dflyway.password=changeme
```

Confirm the migration ran:

```powershell
mvn flyway:info -Dflyway.url=jdbc:postgresql://localhost:5432/instagram `
    -Dflyway.user=instagram -Dflyway.password=changeme
```

Expected output: `V4__add_performance_indexes.sql` shows as `Success`.

---

### 5. Re-run EXPLAIN ANALYZE and compare

Run the same five queries from step 1 again. You should see the planner switch from `Seq Scan` to `Index Scan` for the queries that were previously unindexed.

Update the "after" section in `docs/infra/query-baseline.md` with the new execution times.

Example expected improvement:

| Query | Before | After |
|---|---|---|
| Home feed (20 posts) | Seq Scan 12 ms | Index Scan 0.4 ms |
| Notifications (20 items) | Seq Scan 8 ms | Index Scan 0.2 ms |
| Comments (50 items) | Index Scan 0.3 ms | Index Scan 0.3 ms (already indexed) |

---

## Checklist

- [ ] Audit the hot queries (feed, profile, follow graph, search, notifications) with `EXPLAIN ANALYZE` and list the ones doing sequential scans
- [ ] Add a Flyway migration creating the missing indexes (FKs, `created_at` sort keys, `(user_id, created_at)` composites for cursor paging)
- [ ] Add partial indexes where useful (e.g. `WHERE deleted_at IS NULL` on `posts`)
- [ ] Re-run `EXPLAIN ANALYZE` and confirm the planner switched to index scans
- [ ] Avoid over-indexing — note any index you considered and rejected because it would hurt write throughput

---

## How to Verify

**Migration applied:**

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;
```

Expected: `V4 | add_performance_indexes | true`

**Indexes created:**

```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename IN ('posts', 'follows', 'user_stats')
  AND indexname LIKE 'idx_%'
ORDER BY tablename, indexname;
```

Expected: `idx_posts_not_deleted`, `idx_posts_cursor`, `idx_follows_following_approved`, `idx_user_stats_followers` appear in the list.

**EXPLAIN ANALYZE shows Index Scan:**

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.* FROM posts p
WHERE p.user_id = '<user_id>'::uuid
  AND p.deleted_at IS NULL
ORDER BY p.created_at DESC
LIMIT 20;
```

Expected: the plan shows `Index Scan using idx_posts_not_deleted` (or a Bitmap Index Scan on it), not a `Seq Scan`.

---

## Notes / Gotchas

**"The planner still shows Seq Scan even after adding the index."**
Postgres maintains row count statistics (`ANALYZE`) that the planner uses to decide whether an index is worthwhile. After inserting test data and creating indexes, run `ANALYZE posts;` to refresh the statistics:

```sql
ANALYZE posts;
ANALYZE follows;
```

Then re-run `EXPLAIN ANALYZE`. The planner will often switch to the index after a fresh `ANALYZE`.

**"CREATE INDEX CONCURRENTLY fails inside a Flyway migration."**
Flyway wraps migrations in a transaction, and `CREATE INDEX CONCURRENTLY` is not allowed inside a transaction. Use `CREATE INDEX IF NOT EXISTS` (without `CONCURRENTLY`) instead. The lock duration is brief for empty or small tables in development. In production, apply such migrations manually or use a Flyway callback to run them outside the transaction boundary.

**"I added an index but the query got slower."**
This happens when the index is very wide (many columns) and the query updates the table frequently. Every `INSERT`/`UPDATE` must update all indexes. If a table is write-heavy and the query is not on a critical path, the index may not be worth it. Document the reasoning in a comment in the SQL file — the checklist item "Avoid over-indexing" is specifically about recording this decision.

**"The Flyway migration checksum changes if I edit V4."**
Once a Flyway migration is applied, its checksum is stored. Editing the file after application causes Flyway to throw `ERROR: Migration checksum mismatch`. If you need to fix a migration in development, either create a new `V5__fix_indexes.sql` or reset the Flyway history: `mvn flyway:repair` or `mvn flyway:clean` (destructive — drops the schema).

**Official PostgreSQL index documentation:**
https://www.postgresql.org/docs/15/indexes.html

**Cross-task references:**
- TASK-10.1 produced the baseline that this task improves. Update `docs/infra/query-baseline.md` with the new numbers.
- TASK-10.8 (keyset pagination) requires the composite `(created_at DESC, id DESC)` index added in step 3 — coordinate to avoid creating it twice.
- TASK-10.3 (Redis caching) hides slow queries on cache hits; this task fixes them for cache misses.

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **How database indexes work** — B-tree structure and lookup cost — https://use-the-index-luke.com/
- **Index types (B-tree, GIN, GiST)** — choosing the right one per column — https://www.postgresql.org/docs/current/indexes-types.html
- **Multicolumn & covering indexes** — column order and index-only scans — https://www.postgresql.org/docs/current/indexes-multicolumn.html
- **Finding unused indexes** — read `pg_stat_user_indexes` before adding/dropping — https://www.postgresql.org/docs/current/monitoring-stats.html

### Official docs (code reference)
- **PostgreSQL indexes** — https://www.postgresql.org/docs/current/indexes.html
- **Using EXPLAIN** — https://www.postgresql.org/docs/current/using-explain.html
