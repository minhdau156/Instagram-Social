# Database Index

## What is it
A database index is a separate, sorted data structure (most commonly a B-Tree, sometimes a Hash or GIN/GiST index) that the database maintains alongside a table to speed up lookups on one or more columns. Instead of scanning every row in the table to find matches (a "sequential scan" / "full table scan"), the database can navigate the index's ordered structure to jump directly to the matching rows — similar to how a book's index lets you find a topic without reading every page.

An index is built on specific column(s), e.g. `CREATE INDEX idx_users_email ON users(email);`. It stores the indexed column's values plus a pointer (row ID / ctid) back to the actual row in the table.

## Why use it
- **Speed**: turns an O(n) full table scan into an O(log n) lookup for equality/range queries — critical once tables grow past a few thousand rows.
- **Enforces uniqueness**: unique indexes (and primary keys, which are backed by one) guarantee no duplicate values without the app having to check manually.
- **Speeds up JOINs**: foreign key columns are prime index candidates since JOINs constantly match rows across tables.
- **Speeds up ORDER BY / GROUP BY**: an index already stores data in sorted order, so the database can skip a separate sort step.
- **Trade-off to know**: indexes aren't free — each one adds storage overhead and slows down `INSERT`/`UPDATE`/`DELETE` (because the index must be updated too). Over-indexing a write-heavy table can hurt more than it helps.

## How to use it
1. **Identify hot columns**: look at `WHERE`, `JOIN ON`, `ORDER BY`, and `GROUP BY` clauses in your most frequent/slow queries.
2. **Create the index**:
   - Single column: `CREATE INDEX idx_posts_user_id ON posts(user_id);`
   - Composite (multi-column): `CREATE INDEX idx_posts_user_created ON posts(user_id, created_at);` — column order matters; put the most selective/most-frequently-filtered column first (or the one used in equality checks before range checks).
   - Unique: `CREATE UNIQUE INDEX idx_users_email ON users(email);`
3. **Verify it's actually used**: run `EXPLAIN ANALYZE SELECT ...` and check the plan uses an "Index Scan" / "Index Only Scan" instead of a "Seq Scan".
4. **In JPA/Hibernate**, declare it on the entity so it's created by the schema/migration:
   ```java
   @Table(name = "posts", indexes = {
       @Index(name = "idx_posts_user_id", columnList = "user_id")
   })
   ```
   Or manage it explicitly via a Flyway/Liquibase migration for production control.
5. **Maintain it**: periodically check for unused indexes (`pg_stat_user_indexes` in Postgres) and drop ones that never get hit — they're pure write-overhead at that point.

## When to use it in real life
- **Login lookups**: indexing `users.email` or `users.username` so login queries (`WHERE email = ?`) are instant even with millions of users.
- **Feed/timeline queries**: indexing `posts(user_id, created_at)` so "get this user's posts, newest first" avoids scanning the whole posts table (directly relevant to an Instagram-style feed).
- **Foreign keys**: indexing `comments.post_id`, `likes.post_id`, `follows.follower_id` — anywhere a JOIN or "get all children of this parent" query happens often.
- **Search/autocomplete**: using specialized indexes (GIN with `pg_trgm`, or a full-text index) for `LIKE '%term%'` or search-as-you-type features that a plain B-Tree index can't accelerate well.
- **Uniqueness constraints**: a unique index on `(user_id, post_id)` in a `likes` table to prevent a user liking the same post twice, enforced at the database level instead of only in application code.
