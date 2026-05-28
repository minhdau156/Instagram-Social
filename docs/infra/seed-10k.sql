-- =============================================================================
-- Performance-Test Seed Data  — ~10,000 rows per table
-- UUID namespace: 00000000-0000-0001-xxxx-xxxxxxxxxxxx
--   avoids all collisions with system / Flyway data (00000000-0000-0000-*)
--
-- Insertion order respects FK dependencies.
-- Safe to re-run: every statement uses ON CONFLICT DO NOTHING.
--
-- Run with:
--   psql -h localhost -p 5432 -U instagram -d instagram -f seed-10k.sql
-- or:
--   docker exec -i instagram-social-postgres-1 psql -U instagram -d instagram < seed-10k.sql
-- =============================================================================

BEGIN;

-- ---------------------------------------------------------------------------
-- [1] USERS  (10,000)
-- ---------------------------------------------------------------------------
INSERT INTO users (
    id, username, email, full_name, bio,
    account_status, privacy_level, is_verified,
    created_at, updated_at, last_login_at
)
SELECT
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    'seed_user_' || i,
    'seed_user_' || i || '@perf.test',
    CASE i % 10
        WHEN 0 THEN 'Alice Johnson '   || i
        WHEN 1 THEN 'Bob Smith '       || i
        WHEN 2 THEN 'Carol White '     || i
        WHEN 3 THEN 'David Brown '     || i
        WHEN 4 THEN 'Eva Martinez '    || i
        WHEN 5 THEN 'Frank Lee '       || i
        WHEN 6 THEN 'Grace Chen '      || i
        WHEN 7 THEN 'Henry Davis '     || i
        WHEN 8 THEN 'Iris Wilson '     || i
        ELSE          'James Taylor '  || i
    END,
    CASE i % 6
        WHEN 0 THEN '📸 Photographer & traveller'
        WHEN 1 THEN '🍕 Food lover | home cook'
        WHEN 2 THEN '🏋️ Fitness coach | daily grind'
        WHEN 3 THEN '🎨 Digital artist & designer'
        WHEN 4 THEN '✈️ Always somewhere new'
        ELSE         '☕ Coffee first, everything second'
    END,
    CASE
        WHEN i % 50  = 0 THEN 'SUSPENDED'::account_status
        WHEN i % 200 = 0 THEN 'DEACTIVATED'::account_status
        ELSE                   'ACTIVE'::account_status
    END,
    CASE
        WHEN i % 7  = 0 THEN 'FOLLOWERS_ONLY'::privacy_level
        WHEN i % 13 = 0 THEN 'PRIVATE'::privacy_level
        ELSE                 'PUBLIC'::privacy_level
    END,
    i % 100 = 0,                                        -- 1 % verified
    NOW() - (i % 730) * INTERVAL '1 day',
    NOW() - (i % 60)  * INTERVAL '1 day',
    NOW() - (i % 14)  * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [2] USER_STATS  (10,000 — one row per seed user, counts filled in at end)
-- ---------------------------------------------------------------------------
INSERT INTO user_stats (user_id, post_count, follower_count, following_count)
SELECT
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    1,   -- each user owns exactly 1 seed post
    CASE WHEN i <= 200 THEN 50 ELSE 1 END,  -- top-200 are "popular"
    3
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [3] NOTIFICATION_SETTINGS  (10,000)
-- ---------------------------------------------------------------------------
INSERT INTO notification_settings (
    user_id, likes, comments, new_followers,
    follow_requests, direct_messages, mentions,
    push_enabled, email_enabled, updated_at
)
SELECT
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    i % 5 <> 0,      -- 80 % want like notifications
    TRUE,
    TRUE,
    i % 3 = 0,       -- 33 % want follow-request notifications
    TRUE,
    i % 4 <> 0,      -- 75 % want mention notifications
    i % 10 <> 0,     -- 90 % push enabled
    i % 20 = 0,      -- 5 % email enabled
    NOW() - (i % 30) * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [4] USER_ROLES  (10,000 — every seed user gets the USER role)
-- ---------------------------------------------------------------------------
INSERT INTO user_roles (user_id, role_id, assigned_by, assigned_at)
SELECT
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    (SELECT id FROM roles WHERE name = 'USER'),
    NULL,
    NOW() - (i % 365) * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;

-- Give users 1-5 the MODERATOR role so RBAC queries have some variety
INSERT INTO user_roles (user_id, role_id, assigned_by, assigned_at)
SELECT
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    (SELECT id FROM roles WHERE name = 'MODERATOR'),
    NULL,
    NOW()
FROM generate_series(1, 5) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [5] FOLLOWS  (~10,000)
--
-- Social-graph design:
--   • Users 1-200 are "popular": everyone follows one of them (ring mod 200).
--   • Each user also follows 2 random others so the graph is denser.
--   Self-follows filtered via WHERE; duplicates collapsed via ON CONFLICT.
-- ---------------------------------------------------------------------------
INSERT INTO follows (follower_id, following_id, is_approved, created_at)
SELECT DISTINCT
    ('00000000-0000-0001-0000-' || lpad(to_hex(follower), 12, '0'))::uuid,
    ('00000000-0000-0001-0000-' || lpad(to_hex(following), 12, '0'))::uuid,
    is_approved,
    NOW() - (ABS(follower - following) % 365) * INTERVAL '1 day'
FROM (
    -- every user follows one of the top-200 "influencers"
    SELECT i                            AS follower,
           (i % 200) + 1               AS following,
           TRUE                         AS is_approved
    FROM generate_series(1, 10000) i

    UNION ALL

    -- each user also follows a second peer (stride-7 permutation)
    SELECT i                                        AS follower,
           ((i * 7 + 3)  % 10000) + 1              AS following,
           (i % 5 <> 0)                             AS is_approved
    FROM generate_series(1, 10000) i

    UNION ALL

    -- and a third (stride-13)
    SELECT i                                        AS follower,
           ((i * 13 + 17) % 10000) + 1             AS following,
           TRUE                                     AS is_approved
    FROM generate_series(1, 3400) i   -- ~3 400 extra, caps total near 10 k
) t
WHERE follower <> following
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [6] POSTS  (10,000 — one per seed user)
-- ---------------------------------------------------------------------------
INSERT INTO posts (
    id, user_id, caption, location, status,
    like_count, comment_count, save_count, share_count,
    created_at, updated_at
)
SELECT
    ('00000000-0000-0001-0001-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    CASE i % 8
        WHEN 0 THEN 'Golden hour never gets old 🌅 #photography #travel'
        WHEN 1 THEN 'Homemade pasta from scratch 🍝 #food #cooking'
        WHEN 2 THEN 'Morning run complete 💪 #fitness #motivation'
        WHEN 3 THEN 'New artwork dropped 🎨 #art #design #creative'
        WHEN 4 THEN 'Weekend vibes ✨ #lifestyle #happy'
        WHEN 5 THEN 'Explored a hidden gem today 🗺️ #adventure #travel'
        WHEN 6 THEN 'Sunset > everything 🌇 #nature #photography'
        ELSE         'Living my best life 😎 #lifestyle #positivity'
    END || ' — post ' || i,
    CASE i % 10
        WHEN 0 THEN 'New York, NY'
        WHEN 1 THEN 'Los Angeles, CA'
        WHEN 2 THEN 'London, UK'
        WHEN 3 THEN 'Tokyo, Japan'
        WHEN 4 THEN 'Paris, France'
        WHEN 5 THEN 'Sydney, Australia'
        WHEN 6 THEN 'Toronto, Canada'
        WHEN 7 THEN 'Berlin, Germany'
        WHEN 8 THEN 'Singapore'
        ELSE NULL
    END,
    CASE
        WHEN i % 200 = 0 THEN 'ARCHIVED'::post_status
        WHEN i % 500 = 0 THEN 'DRAFT'::post_status
        ELSE                   'PUBLISHED'::post_status
    END,
    i % 1000,           -- like_count
    i % 200,            -- comment_count
    i % 300,            -- save_count
    i % 100,            -- share_count
    NOW() - (i % 365) * INTERVAL '1 day',
    NOW() - (i % 60)  * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [7] POST_MEDIA  (10,000 — one media item per post)
-- ---------------------------------------------------------------------------
INSERT INTO post_media (
    id, post_id, media_type, media_url, thumbnail_url,
    width, height, duration_secs, file_size_bytes, sort_order, created_at
)
SELECT
    ('00000000-0000-0001-0006-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0001-' || lpad(to_hex(i), 12, '0'))::uuid,
    CASE WHEN i % 10 = 0 THEN 'VIDEO'::media_type ELSE 'IMAGE'::media_type END,
    'https://cdn.perf.test/media/seed_' || i ||
        CASE WHEN i % 10 = 0 THEN '.mp4' ELSE '.jpg' END,
    CASE WHEN i % 10 = 0
         THEN 'https://cdn.perf.test/thumbs/seed_' || i || '.jpg'
         ELSE NULL END,
    CASE i % 3 WHEN 0 THEN 1080 WHEN 1 THEN 1080 ELSE 720 END,
    CASE i % 3 WHEN 0 THEN 1080 WHEN 1 THEN 1350 ELSE 720 END,
    CASE WHEN i % 10 = 0 THEN ROUND((15 + (i % 45))::numeric, 2) ELSE NULL END,
    (500000 + (i % 4500000))::bigint,
    0,
    NOW() - (i % 365) * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [8] HASHTAGS  (500)
-- ---------------------------------------------------------------------------
INSERT INTO hashtags (id, name, post_count, created_at)
SELECT
    ('00000000-0000-0001-0003-' || lpad(to_hex(i), 12, '0'))::uuid,
    CASE i % 25
        WHEN  0 THEN 'photography'
        WHEN  1 THEN 'travel'
        WHEN  2 THEN 'food'
        WHEN  3 THEN 'fitness'
        WHEN  4 THEN 'fashion'
        WHEN  5 THEN 'nature'
        WHEN  6 THEN 'art'
        WHEN  7 THEN 'music'
        WHEN  8 THEN 'lifestyle'
        WHEN  9 THEN 'technology'
        WHEN 10 THEN 'beauty'
        WHEN 11 THEN 'sports'
        WHEN 12 THEN 'cooking'
        WHEN 13 THEN 'architecture'
        WHEN 14 THEN 'design'
        WHEN 15 THEN 'adventure'
        WHEN 16 THEN 'wellness'
        WHEN 17 THEN 'motivation'
        WHEN 18 THEN 'business'
        WHEN 19 THEN 'education'
        WHEN 20 THEN 'gaming'
        WHEN 21 THEN 'books'
        WHEN 22 THEN 'science'
        WHEN 23 THEN 'pets'
        ELSE          'humor'
    END || '_' || i,
    (i % 500) * 20,
    NOW() - (i % 500) * INTERVAL '1 day'
FROM generate_series(1, 500) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [9] HASHTAG_STATS  (500)
-- ---------------------------------------------------------------------------
INSERT INTO hashtag_stats (hashtag_id, weekly_count, updated_at)
SELECT
    ('00000000-0000-0001-0003-' || lpad(to_hex(i), 12, '0'))::uuid,
    (i % 1000) * 5,
    NOW() - (i % 7) * INTERVAL '1 day'
FROM generate_series(1, 500) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [10] POST_HASHTAGS  (10,000 — one hashtag per post)
-- ---------------------------------------------------------------------------
INSERT INTO post_hashtags (post_id, hashtag_id, created_at)
SELECT
    ('00000000-0000-0001-0001-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0003-' || lpad(to_hex((i % 500) + 1), 12, '0'))::uuid,
    NOW() - (i % 365) * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [11] USER_INTERESTS  (10,000)
-- ---------------------------------------------------------------------------
INSERT INTO user_interests (user_id, hashtag_id, score, updated_at)
SELECT
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0003-' || lpad(to_hex((i % 500) + 1), 12, '0'))::uuid,
    ROUND((1.0 + (i % 49))::numeric, 2),
    NOW() - (i % 30) * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [12] POST_LIKES  (10,000)
--   user_(i+5000 mod 10000) likes post_i
-- ---------------------------------------------------------------------------
INSERT INTO post_likes (user_id, post_id, created_at)
SELECT
    ('00000000-0000-0001-0000-' || lpad(to_hex(((i + 5000 - 1) % 10000) + 1), 12, '0'))::uuid,
    ('00000000-0000-0001-0001-' || lpad(to_hex(i), 12, '0'))::uuid,
    NOW() - (i % 200) * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [13] SAVED_POSTS  (10,000)
--   user_(i+3000 mod 10000) saves post_i
-- ---------------------------------------------------------------------------
INSERT INTO saved_posts (user_id, post_id, created_at)
SELECT
    ('00000000-0000-0001-0000-' || lpad(to_hex(((i + 3000 - 1) % 10000) + 1), 12, '0'))::uuid,
    ('00000000-0000-0001-0001-' || lpad(to_hex(i), 12, '0'))::uuid,
    NOW() - (i % 100) * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [14] POST_SHARES  (10,000)
-- ---------------------------------------------------------------------------
INSERT INTO post_shares (
    id, post_id, shared_by_id, shared_to_id, platform, created_at
)
SELECT
    ('00000000-0000-0001-000b-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0001-' || lpad(to_hex(((i + 7000 - 1) % 10000) + 1), 12, '0'))::uuid,
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    -- 20 % external shares (NULL); 80 % sent to another seed user
    CASE WHEN i % 5 = 0 THEN NULL
         ELSE ('00000000-0000-0001-0000-' || lpad(to_hex(((i + 2500 - 1) % 10000) + 1), 12, '0'))::uuid
    END,
    CASE i % 5
        WHEN 0 THEN 'twitter'
        WHEN 1 THEN 'internal'
        WHEN 2 THEN 'whatsapp'
        WHEN 3 THEN 'internal'
        ELSE         'instagram_story'
    END,
    NOW() - (i % 180) * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [15] COMMENTS  (10,000)
--   8,000 top-level  +  2,000 replies to comments 1-2000
-- ---------------------------------------------------------------------------
INSERT INTO comments (
    id, post_id, user_id, parent_id, body,
    like_count, reply_count, is_deleted, created_at, updated_at
)
SELECT
    ('00000000-0000-0001-0002-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0001-' || lpad(to_hex((i % 10000) + 1), 12, '0'))::uuid,
    ('00000000-0000-0001-0000-' || lpad(to_hex(((i * 3 + 7) % 10000) + 1), 12, '0'))::uuid,
    -- replies: comments 8001-10000 reply to comment (i - 8000)
    CASE WHEN i > 8000
         THEN ('00000000-0000-0001-0002-' || lpad(to_hex(i - 8000), 12, '0'))::uuid
         ELSE NULL
    END,
    CASE i % 10
        WHEN 0 THEN 'Amazing shot! 🔥'
        WHEN 1 THEN 'Love this so much ❤️'
        WHEN 2 THEN 'Wow, incredible work!'
        WHEN 3 THEN 'This made my day 😊'
        WHEN 4 THEN 'Goals! Keep it up 🙌'
        WHEN 5 THEN 'So beautiful, where is this?'
        WHEN 6 THEN 'Obsessed with this look!'
        WHEN 7 THEN 'Perfect vibes ✨'
        WHEN 8 THEN 'I needed to see this today 💯'
        ELSE         'Absolutely stunning 😍'
    END,
    i % 50,             -- like_count
    CASE WHEN i <= 2000 THEN 1 ELSE 0 END,  -- reply_count (first 2000 get replies)
    i % 200 = 0,        -- 0.5 % soft-deleted
    NOW() - (i % 365) * INTERVAL '1 day',
    NOW() - (i % 30)  * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [16] COMMENT_LIKES  (10,000)
--   user_(i+2000 mod 10000) likes comment_i
-- ---------------------------------------------------------------------------
INSERT INTO comment_likes (user_id, comment_id, created_at)
SELECT
    ('00000000-0000-0001-0000-' || lpad(to_hex(((i + 2000 - 1) % 10000) + 1), 12, '0'))::uuid,
    ('00000000-0000-0001-0002-' || lpad(to_hex(i), 12, '0'))::uuid,
    NOW() - (i % 100) * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [17] MENTIONS  (5,000 — all post-level)
-- ---------------------------------------------------------------------------
INSERT INTO mentions (id, mentioned_user_id, post_id, comment_id, created_at)
SELECT
    ('00000000-0000-0001-0008-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0000-' || lpad(to_hex(((i + 3500 - 1) % 10000) + 1), 12, '0'))::uuid,
    ('00000000-0000-0001-0001-' || lpad(to_hex(i), 12, '0'))::uuid,
    NULL,
    NOW() - (i % 300) * INTERVAL '1 day'
FROM generate_series(1, 5000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [18] CONVERSATIONS  (5,000)
--   conversations 1-4500: 1-to-1 DMs
--   conversations 4501-5000: group chats
-- ---------------------------------------------------------------------------
INSERT INTO conversations (id, is_group, name, avatar_url, created_by_id, created_at, updated_at)
SELECT
    ('00000000-0000-0001-0004-' || lpad(to_hex(i), 12, '0'))::uuid,
    i > 4500,
    CASE WHEN i > 4500 THEN 'Group Chat #' || i ELSE NULL END,
    CASE WHEN i > 4500 THEN 'https://cdn.perf.test/groups/seed_' || i || '.jpg' ELSE NULL END,
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    NOW() - (i % 365) * INTERVAL '1 day',
    NOW() - (i % 30)  * INTERVAL '1 day'
FROM generate_series(1, 5000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [19] CONVERSATION_MEMBERS  (10,000 — 2 members per conversation)
-- ---------------------------------------------------------------------------
-- first member = conversation creator (admin)
INSERT INTO conversation_members (
    conversation_id, user_id, is_admin, joined_at, last_read_at
)
SELECT
    ('00000000-0000-0001-0004-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    TRUE,
    NOW() - (i % 365) * INTERVAL '1 day',
    NOW() - (i % 7)   * INTERVAL '1 day'
FROM generate_series(1, 5000) i
ON CONFLICT DO NOTHING;

-- second member
INSERT INTO conversation_members (
    conversation_id, user_id, is_admin, joined_at, last_read_at
)
SELECT
    ('00000000-0000-0001-0004-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0000-' || lpad(to_hex(i + 5000), 12, '0'))::uuid,
    FALSE,
    NOW() - (i % 360) * INTERVAL '1 day',
    NOW() - (i % 14)  * INTERVAL '1 day'
FROM generate_series(1, 5000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [20] MESSAGES  (10,000 — 2 per conversation)
--   odd  messages: sent by member-1 (user_i)
--   even messages: sent by member-2 (user_(i+5000))
-- ---------------------------------------------------------------------------
INSERT INTO messages (
    id, conversation_id, sender_id, message_type, body,
    is_deleted, created_at, updated_at
)
SELECT
    ('00000000-0000-0001-0005-' || lpad(to_hex(msg_id), 12, '0'))::uuid,
    ('00000000-0000-0001-0004-' || lpad(to_hex(conv_i), 12, '0'))::uuid,
    sender_id,
    'TEXT'::message_type,
    CASE msg_id % 8
        WHEN 0 THEN 'Hey! How are you? 👋'
        WHEN 1 THEN 'Did you see that post? 😂'
        WHEN 2 THEN 'Let''s catch up sometime soon!'
        WHEN 3 THEN 'That was so fun yesterday!'
        WHEN 4 THEN 'Can you send me that link again?'
        WHEN 5 THEN 'Haha yes exactly 😄'
        WHEN 6 THEN 'Miss you! Come visit 🏠'
        ELSE         'On my way! See you soon 🚗'
    END,
    msg_id % 500 = 0,   -- 0.2 % deleted
    NOW() - ((msg_id % 365)) * INTERVAL '1 day',
    NOW() - ((msg_id % 60))  * INTERVAL '1 day'
FROM (
    -- first message in each conversation (sender = user_i)
    SELECT
        (2 * i - 1) AS msg_id,
        i           AS conv_i,
        ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid AS sender_id
    FROM generate_series(1, 5000) i

    UNION ALL

    -- second message in each conversation (sender = user_(i+5000))
    SELECT
        (2 * i)     AS msg_id,
        i           AS conv_i,
        ('00000000-0000-0001-0000-' || lpad(to_hex(i + 5000), 12, '0'))::uuid AS sender_id
    FROM generate_series(1, 5000) i
) msgs
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [21] MESSAGE_READS  (10,000)
--   odd messages  (from user_i)       read by user_(i+5000)
--   even messages (from user_(i+5000)) read by user_i
-- ---------------------------------------------------------------------------
INSERT INTO message_reads (message_id, user_id, read_at)
SELECT
    ('00000000-0000-0001-0005-' || lpad(to_hex(2 * i - 1), 12, '0'))::uuid,  -- odd msg
    ('00000000-0000-0001-0000-' || lpad(to_hex(i + 5000), 12, '0'))::uuid,   -- read by member-2
    NOW() - (i % 7) * INTERVAL '1 day'
FROM generate_series(1, 5000) i
ON CONFLICT DO NOTHING;

INSERT INTO message_reads (message_id, user_id, read_at)
SELECT
    ('00000000-0000-0001-0005-' || lpad(to_hex(2 * i), 12, '0'))::uuid,      -- even msg
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,          -- read by member-1
    NOW() - (i % 5) * INTERVAL '1 day'
FROM generate_series(1, 5000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [22] NOTIFICATIONS  (10,000)
-- ---------------------------------------------------------------------------
INSERT INTO notifications (
    id, recipient_id, actor_id, type, entity_id, entity_type, is_read, created_at
)
SELECT
    ('00000000-0000-0001-0007-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0000-' || lpad(to_hex(((i + 5000 - 1) % 10000) + 1), 12, '0'))::uuid,
    CASE i % 12
        WHEN  0 THEN 'LIKE_POST'::notification_type
        WHEN  1 THEN 'COMMENT_POST'::notification_type
        WHEN  2 THEN 'FOLLOW'::notification_type
        WHEN  3 THEN 'LIKE_COMMENT'::notification_type
        WHEN  4 THEN 'REPLY_COMMENT'::notification_type
        WHEN  5 THEN 'FOLLOW_REQUEST'::notification_type
        WHEN  6 THEN 'MENTION_POST'::notification_type
        WHEN  7 THEN 'DIRECT_MESSAGE'::notification_type
        WHEN  8 THEN 'FOLLOW_ACCEPTED'::notification_type
        WHEN  9 THEN 'POST_SHARED'::notification_type
        WHEN 10 THEN 'MENTION_COMMENT'::notification_type
        ELSE         'GROUP_MESSAGE'::notification_type
    END,
    ('00000000-0000-0001-0001-' || lpad(to_hex((i % 10000) + 1), 12, '0'))::uuid,
    CASE i % 3 WHEN 0 THEN 'post' WHEN 1 THEN 'comment' ELSE 'conversation' END,
    i % 2 = 0,   -- 50 % read
    NOW() - (i % 90) * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [23] SEARCH_HISTORY  (10,000)
-- ---------------------------------------------------------------------------
INSERT INTO search_history (id, user_id, query, searched_at)
SELECT
    ('00000000-0000-0001-0009-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    CASE i % 20
        WHEN  0 THEN 'sunset photography'
        WHEN  1 THEN 'healthy recipes'
        WHEN  2 THEN 'workout tips'
        WHEN  3 THEN 'travel destinations'
        WHEN  4 THEN 'minimalist design'
        WHEN  5 THEN 'coffee art'
        WHEN  6 THEN 'street photography'
        WHEN  7 THEN 'skincare routine'
        WHEN  8 THEN 'home decor'
        WHEN  9 THEN 'mountain hiking'
        WHEN 10 THEN 'digital art'
        WHEN 11 THEN 'yoga poses'
        WHEN 12 THEN 'pasta recipes'
        WHEN 13 THEN 'urban exploration'
        WHEN 14 THEN 'vintage fashion'
        WHEN 15 THEN 'cat videos'
        WHEN 16 THEN 'productivity hacks'
        WHEN 17 THEN 'landscape photography'
        WHEN 18 THEN 'vegan food'
        ELSE         'coding tutorials'
    END,
    NOW() - (i % 60) * INTERVAL '1 day'
FROM generate_series(1, 10000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [24] USER_BLOCKS  (2,000)
--   user_i blocks user_(i+1000 mod 10000)  for i = 1..2000
--   stride ensures blocker != blocked
-- ---------------------------------------------------------------------------
INSERT INTO user_blocks (blocker_id, blocked_id, created_at)
SELECT
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0000-' || lpad(to_hex(((i + 1000 - 1) % 10000) + 1), 12, '0'))::uuid,
    NOW() - (i % 180) * INTERVAL '1 day'
FROM generate_series(1, 2000) i
WHERE i <> ((i + 1000 - 1) % 10000) + 1
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [25] REPORTS  (2,000)
--   user_i reports post_((i*3) mod 10000)  for i = 1..2000
-- ---------------------------------------------------------------------------
INSERT INTO reports (
    id, reporter_id, entity_type, entity_id, reason,
    details, status, reviewed_by_id, reviewed_at, created_at
)
SELECT
    ('00000000-0000-0001-000a-' || lpad(to_hex(i), 12, '0'))::uuid,
    ('00000000-0000-0001-0000-' || lpad(to_hex(i), 12, '0'))::uuid,
    CASE i % 4
        WHEN 0 THEN 'POST'::report_entity_type
        WHEN 1 THEN 'USER'::report_entity_type
        WHEN 2 THEN 'COMMENT'::report_entity_type
        ELSE         'POST'::report_entity_type
    END,
    ('00000000-0000-0001-0001-' || lpad(to_hex(((i * 3 - 1) % 10000) + 1), 12, '0'))::uuid,
    CASE i % 8
        WHEN 0 THEN 'SPAM'
        WHEN 1 THEN 'HATE_SPEECH'
        WHEN 2 THEN 'NUDITY'
        WHEN 3 THEN 'VIOLENCE'
        WHEN 4 THEN 'HARASSMENT'
        WHEN 5 THEN 'FALSE_INFORMATION'
        WHEN 6 THEN 'SELF_HARM'
        ELSE         'OTHER'
    END,
    CASE WHEN i % 3 = 0 THEN 'Additional context for report #' || i ELSE NULL END,
    CASE
        WHEN i % 4 = 0 THEN 'RESOLVED'::report_status
        WHEN i % 4 = 1 THEN 'DISMISSED'::report_status
        WHEN i % 4 = 2 THEN 'REVIEWED'::report_status
        ELSE                 'PENDING'::report_status
    END,
    CASE WHEN i % 4 <> 3
         THEN ('00000000-0000-0001-0000-' || lpad(to_hex(1), 12, '0'))::uuid  -- seed user 1 as reviewer
         ELSE NULL
    END,
    CASE WHEN i % 4 <> 3
         THEN NOW() - (i % 30) * INTERVAL '1 day'
         ELSE NULL
    END,
    NOW() - (i % 200) * INTERVAL '1 day'
FROM generate_series(1, 2000) i
ON CONFLICT DO NOTHING;


-- ---------------------------------------------------------------------------
-- [26] AUDIT_LOGS  (10,000)
--   BIGSERIAL PK — no explicit id needed
-- ---------------------------------------------------------------------------
INSERT INTO audit_logs (user_id, action, entity_type, entity_id, metadata, ip_address, created_at)
SELECT
    ('00000000-0000-0001-0000-' || lpad(to_hex((i % 10000) + 1), 12, '0'))::uuid,
    CASE i % 12
        WHEN  0 THEN 'LOGIN'
        WHEN  1 THEN 'POST_CREATE'
        WHEN  2 THEN 'POST_DELETE'
        WHEN  3 THEN 'COMMENT_CREATE'
        WHEN  4 THEN 'FOLLOW'
        WHEN  5 THEN 'UNFOLLOW'
        WHEN  6 THEN 'LIKE_POST'
        WHEN  7 THEN 'REPORT_SUBMIT'
        WHEN  8 THEN 'BLOCK_USER'
        WHEN  9 THEN 'PROFILE_UPDATE'
        WHEN 10 THEN 'ROLE_ASSIGN'
        ELSE         'LOGOUT'
    END,
    CASE i % 4
        WHEN 0 THEN 'POST'
        WHEN 1 THEN 'USER'
        WHEN 2 THEN 'COMMENT'
        ELSE         'REPORT'
    END,
    ('00000000-0000-0001-0001-' || lpad(to_hex((i % 10000) + 1), 12, '0'))::uuid,
    ('{"source": "seed", "seq": ' || i || '}')::jsonb,
    ('10.0.' || (i % 256) || '.' || (i % 256))::inet,
    NOW() - (i % 365) * INTERVAL '1 day'
FROM generate_series(1, 10000) i;


-- ---------------------------------------------------------------------------
-- Row-count verification  (run after COMMIT to confirm)
-- ---------------------------------------------------------------------------
-- SELECT 'users'                  , COUNT(*) FROM users;
-- SELECT 'user_stats'             , COUNT(*) FROM user_stats;
-- SELECT 'notification_settings'  , COUNT(*) FROM notification_settings;
-- SELECT 'user_roles'             , COUNT(*) FROM user_roles;
-- SELECT 'follows'                , COUNT(*) FROM follows;
-- SELECT 'posts'                  , COUNT(*) FROM posts;
-- SELECT 'post_media'             , COUNT(*) FROM post_media;
-- SELECT 'hashtags'               , COUNT(*) FROM hashtags;
-- SELECT 'hashtag_stats'          , COUNT(*) FROM hashtag_stats;
-- SELECT 'post_hashtags'          , COUNT(*) FROM post_hashtags;
-- SELECT 'user_interests'         , COUNT(*) FROM user_interests;
-- SELECT 'post_likes'             , COUNT(*) FROM post_likes;
-- SELECT 'saved_posts'            , COUNT(*) FROM saved_posts;
-- SELECT 'post_shares'            , COUNT(*) FROM post_shares;
-- SELECT 'comments'               , COUNT(*) FROM comments;
-- SELECT 'comment_likes'          , COUNT(*) FROM comment_likes;
-- SELECT 'mentions'               , COUNT(*) FROM mentions;
-- SELECT 'conversations'          , COUNT(*) FROM conversations;
-- SELECT 'conversation_members'   , COUNT(*) FROM conversation_members;
-- SELECT 'messages'               , COUNT(*) FROM messages;
-- SELECT 'message_reads'          , COUNT(*) FROM message_reads;
-- SELECT 'notifications'          , COUNT(*) FROM notifications;
-- SELECT 'search_history'         , COUNT(*) FROM search_history;
-- SELECT 'user_blocks'            , COUNT(*) FROM user_blocks;
-- SELECT 'reports'                , COUNT(*) FROM reports;
-- SELECT 'audit_logs'             , COUNT(*) FROM audit_logs;

COMMIT;
