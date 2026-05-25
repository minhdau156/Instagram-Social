# TASK-10.1 — Establish a performance baseline with EXPLAIN ANALYZE

## Overview

Before any optimization work, you need a number to beat. This task teaches you to run PostgreSQL's `EXPLAIN ANALYZE` command against the home-feed query — the most expensive query in the application — and save both the query plan and execution time to `docs/infra/`. Once you have a "before" number, every later task (Redis caching in TASK-10.3, index tuning in TASK-10.7) gives you a concrete "after" number to compare it against. Without this baseline, you are optimizing blind.

---

## Level

Warm-up · Pairs with [TASK-10.3 Redis caching](TASK-10.3-redis-caching.md) / [TASK-10.4 N+1 review](TASK-10.4-n-plus-1-query-review.md) / [TASK-10.7 index audit](TASK-10.7-database-index-audit.md)

---

## Why

Optimising without measuring is guessing. A query that "feels slow" might already be fast, and a query that "seems fine" might be doing a sequential scan over a million rows. `EXPLAIN ANALYZE` makes the PostgreSQL query planner show you its work — which indexes it chose (or didn't), how many rows it expected vs. how many it found, and exactly how many milliseconds each step took. A "before" number is the only way to know whether caching or an index actually helped once you revisit these queries in TASK-10.3 and TASK-10.7.

---

## Prerequisites

- Local PostgreSQL 15 instance is running (started via `docker compose up postgres`).
- At least a few rows of seed data exist (follow a user, create some posts) so the planner has real data to scan.
- You have access to `psql` — the PostgreSQL interactive shell — or a GUI client that can run raw SQL (DBeaver, DataGrip, pgAdmin).
- Familiarity with what the home-feed query does: it joins `follows` → `posts` and returns posts from followed users ordered by `created_at DESC`. The query lives in `FeedJpaRepository.findHomeFeed()`.

**Concepts to skim:**
- `EXPLAIN` vs `EXPLAIN ANALYZE`: `EXPLAIN` shows the plan without running the query; `EXPLAIN ANALYZE` actually runs it and shows real timings.
- Sequential scan (`Seq Scan`): Postgres reads every row in the table. Fine for small tables; slow for large ones.
- Index scan (`Index Scan`): Postgres uses a B-tree or GIN index to jump directly to matching rows.
- "cost=X..Y rows=Z": the planner's estimate of the number of rows and its internal cost unit (not milliseconds).
- "actual time=X..Y rows=Z": the real timings after execution.

---

## Files to Create / Modify

```
docs/infra/query-baseline.md              (new)
docs/infra/                               (new directory — create it if absent)
```

No source code is modified in this task. The deliverable is a saved Markdown document containing the query plans.

---

## Step-by-Step

### 1. Connect to the local database

Open a terminal and connect via `psql`:

```powershell
# PowerShell — connect as the instagram user
psql -h localhost -p 5432 -U instagram -d instagram
```

If `psql` is not on your `PATH`, run it through Docker:

```powershell
docker exec -it instagram-social-postgres-1 psql -U instagram -d instagram
```

You should see the `psql` prompt: `instagram=#`

---

### 2. Seed test data (if the tables are empty)

`EXPLAIN ANALYZE` is only meaningful when rows exist. Check how many posts are in the database:

```sql
SELECT COUNT(*) FROM posts WHERE deleted_at IS NULL;
SELECT COUNT(*) FROM follows WHERE is_approved = TRUE;
```

If either count is 0, insert a handful of rows manually or run the app and create a few posts + follows through the API before continuing. The planner will choose `Seq Scan` regardless of indexes if the table is near-empty, which is misleading.

---

### 3. Find your user ID

Pick the UUID of a user that follows at least one other user who has posts. You can find one like this:

```sql
SELECT f.follower_id, COUNT(p.id) AS followee_posts
FROM follows f
JOIN posts p ON p.user_id = f.following_id AND p.deleted_at IS NULL
WHERE f.is_approved = TRUE
GROUP BY f.follower_id
ORDER BY followee_posts DESC
LIMIT 1;
```

Copy the `follower_id` UUID — you will use it as the `:userId` parameter in the next step.

---

### 4. Run EXPLAIN ANALYZE on the home-feed query

Paste the exact query from `FeedJpaRepository.findHomeFeed()`, wrapping it in `EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)`. The `BUFFERS` flag shows whether Postgres hit shared memory or had to read from disk, which is useful context.

Replace `<YOUR_USER_ID>` with the UUID you found in step 3. Use `NULL` for the cursor (first page) and `20` for the limit:

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT p.*
FROM posts p
JOIN follows f ON f.following_id = p.user_id
WHERE f.follower_id = '<YOUR_USER_ID>'::uuid
  AND f.is_approved = TRUE
  AND (NULL IS NULL OR p.id < NULL::uuid)
  AND p.deleted_at IS NULL
ORDER BY p.created_at DESC
LIMIT 20;
```

PostgreSQL will print the full query plan. It will look something like this (your numbers will differ):

```
Limit  (cost=0.29..43.12 rows=20 width=312) (actual time=0.143..0.281 rows=20 loops=1)
  ->  Nested Loop  (cost=0.29..1234.56 rows=573 width=312) (actual time=0.141..0.275 rows=20 loops=1)
        ->  Index Scan using idx_follows_follower on follows f
              (cost=0.14..45.78 rows=30 width=16) (actual time=0.019..0.043 rows=30 loops=1)
              Index Cond: (follower_id = '<YOUR_USER_ID>'::uuid)
              Filter: is_approved
        ->  Index Scan using idx_posts_user on posts p
              (cost=0.15..19.21 rows=19 width=312) (actual time=0.007..0.007 rows=1 loops=30 loops=1)
              Index Cond: ((user_id = f.following_id) AND (created_at IS NOT NULL))
              Filter: (deleted_at IS NULL)
Planning Time: 0.412 ms
Execution Time: 0.318 ms
```

**What to look for:**
- The last two lines — `Planning Time` and `Execution Time` — are your baseline numbers.
- Look for `Seq Scan` anywhere in the output. A `Seq Scan` on `posts` or `follows` means no index is being used for that step.
- Look for `Index Scan` or `Bitmap Index Scan` — these are fast.

---

### 5. Run EXPLAIN ANALYZE on the explore-feed query

Repeat the process for the explore-feed query from `FeedJpaRepository.findExploreFeed()`:

```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT p.*
FROM posts p
WHERE p.user_id NOT IN (
    SELECT f.following_id
    FROM follows f
    WHERE f.follower_id = '<YOUR_USER_ID>'::uuid
      AND f.is_approved = TRUE
)
  AND p.user_id <> '<YOUR_USER_ID>'::uuid
  AND p.deleted_at IS NULL
  AND (NULL IS NULL OR p.id < NULL::uuid)
ORDER BY (p.like_count + p.comment_count) DESC, p.created_at DESC
LIMIT 20;
```

Note whether Postgres uses a subquery scan (`SubqueryScan`) or can push the filter into an index.

---

### 6. Save the results to docs/infra/query-baseline.md

Create the directory and file:

```powershell
New-Item -ItemType Directory -Force -Path C:\workspace\Instagram-Social\docs\infra
New-Item -ItemType File -Path C:\workspace\Instagram-Social\docs\infra\query-baseline.md
```

Paste the following template and fill in your actual output:

```markdown
# Query Baseline — Before Phase 10 Optimization

Date: <today>
Database rows: posts=<N>, follows=<N>

## Home-Feed Query (cursor=null, limit=20)

### Query plan

\`\`\`
<paste your EXPLAIN ANALYZE output here>
\`\`\`

### Key observations

- Execution Time: X ms
- Planning Time: Y ms
- Planner chose: [Index Scan / Seq Scan] on posts (idx_posts_user)
- Planner chose: [Index Scan / Seq Scan] on follows (idx_follows_follower)

## Explore-Feed Query (cursor=null, limit=20)

### Query plan

\`\`\`
<paste your EXPLAIN ANALYZE output here>
\`\`\`

### Key observations

- Execution Time: X ms
- Planning Time: Y ms
- Planner chose: [describe]

## After TASK-10.3 (Redis cache added)

<!-- Fill in after completing TASK-10.3 -->
- Second identical request latency from Redis: X ms (vs Y ms from Postgres)

## After TASK-10.7 (indexes added)

<!-- Fill in after completing TASK-10.7 -->
- Execution Time: X ms (was Y ms before)
- Planner now chose: [Index Scan / Seq Scan]
```

---

### 7. Re-run after TASK-10.3 and TASK-10.7

Come back to this file after completing those tasks. Add the "after" numbers in the sections at the bottom. The comparison is the proof that the optimizations worked.

---

## Checklist

- [ ] Run `EXPLAIN ANALYZE` on the home-feed query in `psql` and record the execution time
- [ ] Note whether the planner uses an index scan or a sequential scan
- [ ] Re-run after TASK-10.3 / 10.7 and compare the numbers

---

## How to Verify

After step 6, verify the file exists and is non-empty:

```powershell
Get-Item C:\workspace\Instagram-Social\docs\infra\query-baseline.md
Get-Content C:\workspace\Instagram-Social\docs\infra\query-baseline.md | Select-Object -First 20
```

**Passing result:** The file exists, contains at least one `EXPLAIN ANALYZE` output block with a visible `Execution Time:` line, and notes whether the plan used `Index Scan` or `Seq Scan`.

You can also verify that a specific index is in use by checking the schema:

```sql
\d posts
-- Look for: idx_posts_user ON posts USING btree (user_id, created_at DESC)
```

---

## Notes / Gotchas

**"The query runs in 0.1 ms — why bother?"**
With a small local dataset the planner will choose fast paths regardless of missing indexes. This is expected. The baseline is still valuable: when you add real data or run against a staging database with millions of rows, the sequential scans will become obvious. Record what you see now and note the data volume.

**"The planner chose Seq Scan even though there's an index."**
PostgreSQL's planner decides that a sequential scan is cheaper when the table is very small (fewer than ~1000 rows, roughly). This is correct behavior and not a bug. Once the table grows, the planner will switch to index scans automatically — and that is when the index matters. The index audit in TASK-10.7 is about ensuring those indexes exist so the switch happens correctly as the data grows.

**"EXPLAIN ANALYZE actually runs the query — is that a problem?"**
For `SELECT` queries, yes it runs them but no data is modified. The only cost is execution time. If you are concerned about a very slow query on a large dataset, you can run `EXPLAIN (FORMAT TEXT)` (without `ANALYZE`) first to see the estimated plan without running it.

**`psql` not found on Windows.**
Use the Docker exec form shown in step 1. Alternatively, install psql via `winget install PostgreSQL.PostgreSQL` or use DBeaver (a free GUI).

**Cross-task references:**
- TASK-10.7 adds the missing indexes that will change `Seq Scan` → `Index Scan` for the follow-graph and search queries.
- TASK-10.3 adds Redis caching so the second identical request never hits Postgres at all — the comparison to note there is application-level latency, not just query execution time.

---

## Learning Resources

> New to these concepts? Each link below teaches one idea used in this task. Skim the *Concepts* first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Reading a query plan** — interpret EXPLAIN output node by node — https://www.postgresql.org/docs/current/using-explain.html
- **EXPLAIN vs EXPLAIN ANALYZE** — estimate-only plan vs actually-run timings — https://www.postgresql.org/docs/current/sql-explain.html
- **Index scan vs sequential scan** — why the planner picks each, and when an index actually helps — https://use-the-index-luke.com/
- **Buffers / shared hits** — what the `BUFFERS` flag reveals about disk vs memory reads — https://www.postgresql.org/docs/current/using-explain.html

### Official docs (code reference)
- **psql interactive shell** — https://www.postgresql.org/docs/current/app-psql.html
- **PostgreSQL indexes** — https://www.postgresql.org/docs/current/indexes.html
