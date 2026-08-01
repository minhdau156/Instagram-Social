# EXPLAIN ANALYZE

## 1. What is it?

**EXPLAIN ANALYZE** is a SQL diagnostic command (PostgreSQL, MySQL, and most relational databases support a variant) that **actually executes** a query and returns its **real execution plan** — the concrete steps the query planner/optimizer chose to fetch or modify the data, along with **real timing and row-count statistics** for each step.

It differs from plain `EXPLAIN`:
- **`EXPLAIN`** — shows the *estimated* plan only (no execution, no real numbers). Safe to run on anything, including `DELETE`/`UPDATE`, since nothing actually runs.
- **`EXPLAIN ANALYZE`** — *runs the query for real* (side effects included for `INSERT`/`UPDATE`/`DELETE` unless wrapped in a transaction you roll back), then reports both the estimated plan **and** actual measured behavior.

Key pieces of the output (PostgreSQL example):
- **Node type** — `Seq Scan`, `Index Scan`, `Index Only Scan`, `Bitmap Heap Scan`, `Nested Loop`, `Hash Join`, `Sort`, etc.
- **cost=`start..total`** — planner's *estimated* relative cost (arbitrary units, not milliseconds).
- **rows=N** — planner's *estimated* row count for that step.
- **actual time=`start..end`** — real measured time in milliseconds for that node (average over loops).
- **actual rows=N** — real row count returned by that node.
- **loops=N** — how many times that node executed (relevant in nested loops).
- **Planning Time** / **Execution Time** — totals at the bottom.
- `BUFFERS` option (`EXPLAIN (ANALYZE, BUFFERS)`) — adds shared/local buffer hits, reads, dirtied, written — tells you how much came from cache vs. disk.

## 2. Why use it?

- **Estimates vs. reality** — the query planner uses table statistics to *guess* a plan; those stats can be stale or the guess can simply be wrong. `EXPLAIN ANALYZE` shows what *actually* happened, exposing planner misjudgments.
- **Find the real bottleneck** — pinpoints exactly which node (a scan, a join, a sort) consumes the most time, instead of guessing at the query level.
- **Detect missing/unused indexes** — a `Seq Scan` on a large table where an `Index Scan` was expected is a classic smoking gun for a missing index or one the planner refuses to use.
- **Catch bad row-count estimates** — large gaps between `estimated rows` and `actual rows` usually mean stale statistics (needs `ANALYZE`/`VACUUM ANALYZE`) or a bad selectivity assumption, both of which lead to bad plan choices (e.g., nested loop instead of hash join).
- **Validate index/query changes** — before/after comparison proves whether an added index, rewritten query, or changed join actually helped in reality, not just in theory.
- **Justify optimization work with data** — replaces "this feels slow" with concrete numbers (e.g., "this Seq Scan took 480ms and returned 2M rows we then filtered down to 12").

## 3. How can you use it?

**Basic usage:**
```sql
EXPLAIN ANALYZE
SELECT * FROM posts WHERE user_id = 42 ORDER BY created_at DESC LIMIT 20;
```

**With buffers (recommended — shows disk vs. cache I/O):**
```sql
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM posts WHERE user_id = 42 ORDER BY created_at DESC LIMIT 20;
```

**Safe usage for mutating queries (INSERT/UPDATE/DELETE)** — wrap in a transaction and roll back so nothing is actually persisted:
```sql
BEGIN;
EXPLAIN ANALYZE
UPDATE posts SET like_count = like_count + 1 WHERE id = 100;
ROLLBACK;
```

**Reading the output — what to look for:**
1. Read **bottom-up** and **inside-out**: innermost/lowest nodes execute first; their actual time feeds into parent nodes.
2. Compare `estimated rows` vs `actual rows` on each node — a big gap (10x+) signals a statistics problem.
3. Look for `Seq Scan` on large tables in the hot path — a strong hint you need an index, or that an existing index isn't being used (check `WHERE` clause sargability, data types, or function calls wrapping the column).
4. Check `Sort` nodes with high `actual time` — may benefit from an index that already provides the required order (avoids a runtime sort).
5. Check `Nested Loop` with a high `loops` count multiplying a slow inner scan — often better as a `Hash Join`, achievable by fixing statistics or `work_mem`.
6. With `BUFFERS`, watch `shared read` (disk I/O) vs `shared hit` (cache) — lots of reads means data isn't cached, possibly needing more `shared_buffers` or a smaller working set.

**Typical remediation loop:**
```
Run EXPLAIN ANALYZE → find the slow/unexpected node
   → add index / rewrite query / run ANALYZE table → re-run EXPLAIN ANALYZE → confirm improvement
```

**In a Spring Boot / JPA project:** enable SQL logging (`spring.jpa.show-sql` / `logging.level.org.hibernate.SQL=DEBUG`) to capture the exact generated SQL, then paste that query into `psql`/your DB client and run `EXPLAIN ANALYZE` on it directly — JPA-generated queries are often the ones needing this the most (N+1 patterns, missing indexes on `@JoinColumn` foreign keys, etc.).

## 4. When to use it in real life

- **A specific endpoint/API is slow** — pull the exact query it issues (via logs) and run `EXPLAIN ANALYZE` to find which part of the query plan is the bottleneck.
- **Before adding an index** — confirm the current plan really does a full scan and that the new index actually gets picked up by the planner afterward (sometimes it doesn't, e.g., low table cardinality or bad column order in a composite index).
- **After a data volume grows significantly** — a query fast on 1K rows may pick a bad plan at 10M rows; periodic `EXPLAIN ANALYZE` checks on hot queries catch this early.
- **Investigating N+1 query problems in JPA/Hibernate** — confirm whether a "simple" per-row query is actually cheap or secretly expensive at scale.
- **Query/index code review** — validate that a new feature's queries (e.g., a new repository method with `@Query`) perform acceptably before merging, not after a production incident.
- **Choosing between two query rewrites** — e.g., `EXISTS` vs `JOIN` vs `IN` subquery — run each through `EXPLAIN ANALYZE` and compare actual execution time and buffer usage.
- **Debugging a regression after a migration/schema change** — compare plans before/after to see if a dropped index or changed column type silently broke performance.

---

## Summary

`EXPLAIN ANALYZE` actually runs a query and reports its real execution plan, timing, and row counts per step — unlike plain `EXPLAIN`, which only estimates without running.

- **What**: A diagnostic command that executes a query and shows the real (not just estimated) query plan, per-node timing, row counts, and (with `BUFFERS`) disk/cache I/O.
- **Why**: Query planners guess based on statistics that can be stale or wrong; this command reveals what actually happened, exposing missing indexes, bad row estimates, and the true bottleneck node.
- **How**: Run `EXPLAIN (ANALYZE, BUFFERS) <query>`; for mutating queries wrap in `BEGIN; ... ROLLBACK;` to avoid side effects. Read the plan bottom-up, compare estimated vs. actual rows, and look for unexpected `Seq Scan`s, expensive `Sort`s, or `Nested Loop`s with many iterations.
- **When**: Diagnosing slow endpoints, validating a new index before/after, checking query behavior as data grows, investigating JPA/Hibernate N+1 issues, comparing alternative query rewrites, and reviewing new repository queries before merge.
