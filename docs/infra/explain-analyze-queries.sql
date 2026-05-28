-- =============================================================================
-- EXPLAIN ANALYZE Query Catalogue — Phase 10 Performance Baseline
-- Database: analyze_instagram_text
--
-- Connect:
--   docker exec -it instagram-social--postgres-1 psql -U instagram -d analyze_instagram_text
--
-- Test user (has 3 followee posts — good for feed queries):
--   00000000-0000-0001-0000-000000000012
-- =============================================================================


-- -----------------------------------------------------------------------------
-- STEP 0 — Find a good test user (run this first if you want a different one)
-- -----------------------------------------------------------------------------
SELECT f.follower_id, COUNT(p.id) AS followee_posts
FROM follows f
JOIN posts p ON p.user_id = f.following_id AND p.deleted_at IS NULL
WHERE f.is_approved = TRUE
GROUP BY f.follower_id
ORDER BY followee_posts DESC
LIMIT 1;


-- =============================================================================
-- TIER 1 — Called on every page load / scroll
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 1. HOME FEED
--    FeedJpaRepository.findHomeFeed
--    Tables: posts, follows
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT p.*
FROM posts p
JOIN follows f ON f.following_id = p.user_id
WHERE f.follower_id = '00000000-0000-0001-0000-000000000012'::uuid
  AND f.is_approved = TRUE
  AND (NULL IS NULL OR p.id < NULL::uuid)
  AND p.deleted_at IS NULL
ORDER BY p.created_at DESC
LIMIT 20;


-- -----------------------------------------------------------------------------
-- 2. EXPLORE FEED
--    FeedJpaRepository.findExploreFeed
--    Tables: posts, follows (subquery)
--    Watch for: Seq Scan on posts + SubqueryScan
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT p.*
FROM posts p
WHERE p.user_id NOT IN (
    SELECT f.following_id FROM follows f
    WHERE f.follower_id = '00000000-0000-0001-0000-000000000012'::uuid
      AND f.is_approved = TRUE
)
  AND p.user_id <> '00000000-0000-0001-0000-000000000012'::uuid
  AND p.deleted_at IS NULL
  AND (NULL IS NULL OR p.id < NULL::uuid)
ORDER BY (p.like_count + p.comment_count) DESC, p.created_at DESC
LIMIT 20;


-- -----------------------------------------------------------------------------
-- 3. TRENDING HASHTAGS
--    FeedJpaRepository.findTrendingHashtags
--    Tables: hashtags, hashtag_stats
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT h.id, h.name, h.post_count, h.created_at,
       hs.weekly_count, hs.updated_at
FROM hashtags h
JOIN hashtag_stats hs ON hs.hashtag_id = h.id
ORDER BY hs.weekly_count DESC
LIMIT 10;


-- -----------------------------------------------------------------------------
-- 4. NOTIFICATIONS LIST
--    NotificationJpaRepository.findByRecipientIdOrderByCreatedAtDesc
--    Tables: notifications
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM notifications
WHERE recipient_id = '00000000-0000-0001-0000-000000000012'::uuid
ORDER BY created_at DESC
LIMIT 20;


-- -----------------------------------------------------------------------------
-- 5. RBAC PERMISSIONS  ← fires on EVERY authenticated HTTP request
--    UserRoleJpaRepository.findPermissionNamesByUserId
--    Tables: user_roles, roles, role_permissions, permissions
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT p.name
FROM user_roles ur
JOIN roles r ON r.id = ur.role_id
JOIN role_permissions rp ON rp.role_id = r.id
JOIN permissions p ON p.id = rp.permission_id
WHERE ur.user_id = '00000000-0000-0001-0000-000000000012'::uuid;


-- =============================================================================
-- TIER 2 — Called per profile / post visit
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 6. USER PROFILE LOOKUP
--    UserJpaRepository.findByUsername
--    Tables: users
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM users
WHERE username = 'seed_user_12';


-- -----------------------------------------------------------------------------
-- 7. USER'S POSTS (profile grid)
--    PostJpaRepository.findByUserIdAndStatusNot
--    Tables: posts
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM posts
WHERE user_id = '00000000-0000-0001-0000-000000000012'::uuid
  AND status <> 'DELETED'
ORDER BY created_at DESC
LIMIT 12;


-- -----------------------------------------------------------------------------
-- 8. POST MEDIA (batch load alongside feed)
--    PostMediaJpaRepository.findByPostIdIn
--    Tables: post_media
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM post_media
WHERE post_id IN (
    '00000000-0000-0001-0001-000000000001',
    '00000000-0000-0001-0001-000000000002',
    '00000000-0000-0001-0001-000000000003',
    '00000000-0000-0001-0001-000000000004',
    '00000000-0000-0001-0001-000000000005'
)
ORDER BY sort_order ASC;


-- -----------------------------------------------------------------------------
-- 9. COMMENTS — top-level
--    CommentJpaRepository.findTopLevelByPostId
--    Tables: comments
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT c.*
FROM comments c
WHERE c.post_id = '00000000-0000-0001-0001-000000000012'::uuid
  AND c.parent_id IS NULL
  AND c.is_deleted = FALSE
ORDER BY c.created_at ASC
LIMIT 20;


-- -----------------------------------------------------------------------------
-- 10. COMMENT REPLIES
--     CommentJpaRepository.findRepliesByParentId
--     Tables: comments
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT c.*
FROM comments c
WHERE c.parent_id = '00000000-0000-0001-0002-000000000001'::uuid
  AND c.is_deleted = FALSE
ORDER BY c.created_at ASC
LIMIT 20;


-- -----------------------------------------------------------------------------
-- 11. POST LIKERS
--     PostLikeJpaRepository.findByIdPostIdOrderByCreatedAtDesc
--     Tables: post_likes
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM post_likes
WHERE post_id = '00000000-0000-0001-0001-000000000012'::uuid
ORDER BY created_at DESC
LIMIT 20;


-- =============================================================================
-- TIER 3 — Messaging & social graph
-- =============================================================================


-- -----------------------------------------------------------------------------
-- 12. CONVERSATIONS LIST
--     ConversationJpaRepository.findByMemberId
--     Tables: conversations, conversation_members
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT c.*
FROM conversations c
JOIN conversation_members m ON m.conversation_id = c.id
WHERE m.user_id = '00000000-0000-0001-0000-000000000012'::uuid
ORDER BY c.updated_at DESC
LIMIT 20;


-- -----------------------------------------------------------------------------
-- 13. MESSAGES — first page (no cursor)
--     MessageJpaRepository.findByConversationIdOrderByCreatedAtDesc
--     Tables: messages
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM messages
WHERE conversation_id = '00000000-0000-0001-0004-000000000001'::uuid
ORDER BY created_at DESC
LIMIT 30;


-- -----------------------------------------------------------------------------
-- 14. MESSAGES — cursor-based (subsequent pages)
--     MessageJpaRepository.findOlderThanCursor
--     Tables: messages (self-join via subquery)
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT m.*
FROM messages m
WHERE m.conversation_id = '00000000-0000-0001-0004-000000000001'::uuid
  AND m.created_at < (
      SELECT m2.created_at FROM messages m2
      WHERE m2.id = '00000000-0000-0001-0005-000000000002'::uuid
  )
ORDER BY m.created_at DESC
LIMIT 30;


-- -----------------------------------------------------------------------------
-- 15. UNREAD MESSAGE COUNT
--     MessageReadJpaRepository.countUnread
--     Tables: messages, message_reads (correlated subquery)
--     Watch for: correlated subquery firing per row
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT COUNT(m)
FROM messages m
WHERE m.conversation_id = '00000000-0000-0001-0004-000000000001'::uuid
  AND m.sender_id <> '00000000-0000-0001-0000-000000000012'::uuid
  AND m.created_at > COALESCE(
      (SELECT r.read_at FROM message_reads r
       WHERE r.message_id = m.id
         AND r.user_id = '00000000-0000-0001-0000-000000000012'::uuid),
      '1970-01-01'::timestamptz
  );


-- -----------------------------------------------------------------------------
-- 16. FOLLOWERS LIST
--     FollowJpaRepository.findByIdFollowingIdAndIsApproved
--     Tables: follows
--     Note: user 1 is "popular" — followed by ~50 seed users
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM follows
WHERE following_id = '00000000-0000-0001-0000-000000000001'::uuid
  AND is_approved = TRUE
ORDER BY created_at DESC
LIMIT 20;


-- -----------------------------------------------------------------------------
-- 17. FOLLOWING LIST
--     FollowJpaRepository.findByIdFollowerIdAndIsApproved
--     Tables: follows
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM follows
WHERE follower_id = '00000000-0000-0001-0000-000000000012'::uuid
  AND is_approved = TRUE
ORDER BY created_at DESC
LIMIT 20;


-- -----------------------------------------------------------------------------
-- 18. SAVED POSTS
--     SavedPostJpaRepository.findByIdUserIdOrderBySavedAtDesc
--     Tables: saved_posts
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT *
FROM saved_posts
WHERE user_id = '00000000-0000-0001-0000-000000000012'::uuid
ORDER BY created_at DESC
LIMIT 20;


-- -----------------------------------------------------------------------------
-- 19. BLOCK FILTER — both directions (runs inside every feed + search)
--     UserBlockJpaRepository.findBlockedIdsByBlockerId
--     UserBlockJpaRepository.findBlockerIdsByBlockedId
--     Tables: user_blocks
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT blocked_id FROM user_blocks
WHERE blocker_id = '00000000-0000-0001-0000-000000000012'::uuid;

EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT blocker_id FROM user_blocks
WHERE blocked_id = '00000000-0000-0001-0000-000000000012'::uuid;


-- -----------------------------------------------------------------------------
-- 20. SEARCH USERS
--     UserJpaRepository.findByUsernameContainingIgnoreCase  (GIN trgm index)
--     Tables: users
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM users 
WHERE search_tsv @@ to_tsquery('seed_user_5')
  AND account_status = 'ACTIVE'
LIMIT 10;


-- -----------------------------------------------------------------------------
-- 21. HASHTAG POSTS
--     SearchJpaAdapter.findPostsByHashtag
--     Tables: posts, post_hashtags, hashtags
-- -----------------------------------------------------------------------------
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT p.*
FROM posts p
JOIN post_hashtags ph ON ph.post_id = p.id
JOIN hashtags h ON h.id = ph.hashtag_id
WHERE h.name = 'photography_0'
  AND p.deleted_at IS NULL
ORDER BY p.created_at DESC
LIMIT 20;


-- =============================================================================
-- REFERENCE — what to look for
-- =============================================================================
--
--  Index Scan          → fast, B-tree lookup                  ✅
--  Bitmap Index Scan   → fast, moderate selectivity           ✅
--  Index Only Scan     → fastest, no heap fetch needed        ✅
--  Seq Scan            → full table read, red flag at scale   ⚠️
--  Nested Loop         → good when inner set is small         ✅
--  Hash Join           → good for larger sets                 ✅
--  Rows Removed by Filter: N  → high N = missing index        ⚠️
--  Execution Time: X ms       → your baseline number
--  Planning Time: Y ms        → planner overhead
--  Buffers: shared hit=N      → pages from memory (good)
--  Buffers: shared read=N     → pages from disk (slow)
--
-- =============================================================================
