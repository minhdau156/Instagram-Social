# TASK-10.7 — Database Index Audit

Connect to the database before running any query:

```powershell
docker exec -it instagram-social-postgres-1 psql -U instagram -d instagram
```

**Test UUIDs (from seed-10k.sql)**

| Alias | UUID |
|---|---|
| user_id | `00000000-0000-0001-0000-000000000012` |
| post_id | `00000000-0000-0001-0001-000000000012` |

---

## Step 1 — Check existing indexes

```sql
SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE tablename IN ('follows', 'posts', 'notifications', 'comments', 'user_stats')
ORDER BY tablename, indexname;
```

---

## Step 2 — EXPLAIN ANALYZE (before migration)

Run each query and record: **Seq Scan vs Index Scan** and **Execution Time ms**.

### Query A — Home feed

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.*
FROM posts p
JOIN follows f ON f.following_id = p.user_id
WHERE f.follower_id = '00000000-0000-0001-0000-000000000012'::uuid
  AND f.is_approved = TRUE
  AND p.deleted_at IS NULL
ORDER BY p.created_at DESC
LIMIT 20;
```

### Query B — Follow graph (followers list)

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT f.*
FROM follows f
WHERE f.following_id = '00000000-0000-0001-0000-000000000001'::uuid
  AND f.is_approved = TRUE
ORDER BY f.created_at DESC
LIMIT 20;
```

### Query C — Notifications for a user

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT n.*
FROM notifications n
WHERE n.recipient_id = '00000000-0000-0001-0000-000000000012'::uuid
ORDER BY n.created_at DESC
LIMIT 20;
```

### Query D — Search users

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT u.*
FROM users u
WHERE u.username ILIKE 'seed_user_%'
ORDER BY u.follower_count DESC
LIMIT 10;
```

### Query E — Comments for a post

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.*
FROM comments c
WHERE c.post_id = '00000000-0000-0001-0001-000000000012'::uuid
  AND NOT c.is_deleted
ORDER BY c.created_at ASC
LIMIT 50;
```

---

## Step 3 — Apply migration

Apply `V5__add_performance_indexes.sql` (starts automatically on app boot, or run manually):

```powershell
cd backend
mvn flyway:migrate `
  -Dflyway.url="jdbc:postgresql://localhost:5432/instagram" `
  -Dflyway.user=instagram `
  -Dflyway.password=changeme
```

Then refresh planner statistics inside `psql`:

```sql
ANALYZE posts;
ANALYZE follows;
ANALYZE user_stats;
```

---

## Step 4 — EXPLAIN ANALYZE (after migration)

Re-run queries A–E from Step 2 and compare. Expected outcome:

| Query | Before | After |
|---|---|---|
| A — Home feed | Seq Scan | Index Scan |
| B — Followers list | Seq Scan | Index Scan |
| C — Notifications | Index Scan | Index Scan (already indexed) |
| D — Search users | Seq Scan | Index Scan |
| E — Comments | Index Scan | Index Scan (already indexed) |

---

## Step 5 — Verify

### Flyway ran V5

```sql
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;
```

Expected: `V5 | add_performance_indexes | true`

### New indexes exist

```sql
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename IN ('posts', 'follows', 'user_stats')
  AND indexname LIKE 'idx_%'
ORDER BY tablename, indexname;
```

Expected indexes: `idx_posts_not_deleted`, `idx_posts_cursor`, `idx_follows_following_approved`, `idx_user_stats_followers`.

### Spot-check — posts by user uses the partial index

```sql
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.*
FROM posts p
WHERE p.user_id = '00000000-0000-0001-0000-000000000012'::uuid
  AND p.deleted_at IS NULL
ORDER BY p.created_at DESC
LIMIT 20;
```

Expected: plan shows `Index Scan using idx_posts_not_deleted`, not `Seq Scan`.
