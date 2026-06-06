-- =============================================================================
-- TASK-10.7 — Database Index Audit
-- Run these in order: step 1 before migration, step 3 after migration.
--
-- Connect:
--   docker exec -it instagram-social-postgres-1 psql -U instagram -d instagram
--
-- Test UUIDs (from seed-10k.sql):
--   user_id  = '00000000-0000-0001-0000-000000000012'
--   post_id  = '00000000-0000-0001-0001-000000000012'
-- =============================================================================


-- =============================================================================
-- STEP 1 — Check existing indexes before migration
-- =============================================================================

SELECT schemaname, tablename, indexname, indexdef
FROM pg_indexes
WHERE tablename IN ('follows', 'posts', 'notifications', 'comments', 'user_stats')
ORDER BY tablename, indexname;


-- =============================================================================
-- STEP 2 — EXPLAIN ANALYZE on the five hot queries (run BEFORE migration)
--          Record: Seq Scan vs Index Scan, Execution Time ms
-- =============================================================================

-- Query A — Home feed
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.*
FROM posts p
JOIN follows f ON f.following_id = p.user_id
WHERE f.follower_id = '00000000-0000-0001-0000-000000000012'::uuid
  AND f.is_approved = TRUE
  AND p.deleted_at IS NULL
ORDER BY p.created_at DESC
LIMIT 20;


-- Query B — Follow graph (followers list)
EXPLAIN (ANALYZE, BUFFERS)
SELECT f.*
FROM follows f
WHERE f.following_id = '00000000-0000-0001-0000-000000000001'::uuid
  AND f.is_approved = TRUE
ORDER BY f.created_at DESC
LIMIT 20;


-- Query C — Notifications for a user
EXPLAIN (ANALYZE, BUFFERS)
SELECT n.*
FROM notifications n
WHERE n.recipient_id = '00000000-0000-0001-0000-000000000012'::uuid
ORDER BY n.created_at DESC
LIMIT 20;


-- Query D — Search users (ILIKE path, when FTS not active)
EXPLAIN (ANALYZE, BUFFERS)
SELECT u.*
FROM users u
WHERE u.username ILIKE 'seed_user_%'
ORDER BY u.follower_count DESC
LIMIT 10;


-- Query E — Comments for a post
EXPLAIN (ANALYZE, BUFFERS)
SELECT c.*
FROM comments c
WHERE c.post_id = '00000000-0000-0001-0001-000000000012'::uuid
  AND NOT c.is_deleted
ORDER BY c.created_at ASC
LIMIT 50;


-- =============================================================================
-- STEP 3 — After applying V5__add_performance_indexes.sql, refresh statistics
-- =============================================================================

ANALYZE posts;
ANALYZE follows;
ANALYZE user_stats;


-- =============================================================================
-- STEP 4 — Re-run the same queries after migration (verify Index Scan)
--          Copy/paste the five queries from STEP 2 and compare output.
-- =============================================================================


-- =============================================================================
-- STEP 5 — Verify migration applied and indexes exist
-- =============================================================================

-- Confirm Flyway ran V5
SELECT version, description, success
FROM flyway_schema_history
ORDER BY installed_rank DESC
LIMIT 5;

-- Confirm the four new indexes are present
SELECT indexname, indexdef
FROM pg_indexes
WHERE tablename IN ('posts', 'follows', 'user_stats')
  AND indexname LIKE 'idx_%'
ORDER BY tablename, indexname;

-- Spot-check: posts by user with deleted_at filter should use idx_posts_not_deleted
EXPLAIN (ANALYZE, BUFFERS)
SELECT p.*
FROM posts p
WHERE p.user_id = '00000000-0000-0001-0000-000000000012'::uuid
  AND p.deleted_at IS NULL
ORDER BY p.created_at DESC
LIMIT 20;
