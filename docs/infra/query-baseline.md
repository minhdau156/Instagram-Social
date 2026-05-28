# Query Baseline — Before Phase 10 Optimization

**Date:**
**Database:** analyze_instagram_text
**Rows:** users=10,001 · posts=10,000 · follows=23,397 · comments=10,000 · messages=10,000 · notifications=10,000

---

## 1. Home Feed

**Source:** `FeedJpaRepository.findHomeFeed`
**Tables:** `posts`, `follows`

### Query plan
```
Limit  (cost=40.26..40.26 rows=3 width=261) (actual time=1.159..1.162 rows=3 loops=1)
  Buffers: shared hit=17
  ->  Sort  (cost=40.26..40.26 rows=3 width=261) (actual time=1.157..1.159 rows=3 loops=1)
        Sort Key: p.created_at DESC
        Sort Method: quicksort  Memory: 26kB
        Buffers: shared hit=17
        ->  Nested Loop  (cost=4.60..40.23 rows=3 width=261) (actual time=0.608..1.005 rows=3 loops=1)
              Buffers: shared hit=14
              ->  Bitmap Heap Scan on follows f  (cost=4.31..15.29 rows=3 width=16) (actual time=0.585..0.593 rows=3 loops=1)
"                    Recheck Cond: (follower_id = '00000000-0000-0001-0000-000000000012'::uuid)"
                    Filter: is_approved
                    Heap Blocks: exact=3
                    Buffers: shared hit=5
                    ->  Bitmap Index Scan on idx_follows_follower  (cost=0.00..4.31 rows=3 width=0) (actual time=0.570..0.570 rows=3 loops=1)
"                          Index Cond: (follower_id = '00000000-0000-0001-0000-000000000012'::uuid)"
                          Buffers: shared hit=2
              ->  Index Scan using idx_posts_user on posts p  (cost=0.29..8.30 rows=1 width=261) (actual time=0.133..0.133 rows=1 loops=3)
                    Index Cond: (user_id = f.following_id)
                    Filter: (deleted_at IS NULL)
                    Buffers: shared hit=9
Planning:
  Buffers: shared hit=17
Planning Time: 0.512 ms
Execution Time: 1.253 ms
```

### Observations
- Execution Time: 1.253 ms
- Planning Time: 0.512 ms
- `follows` scan: Bitmap Index Scan — `idx_follows_follower`
- `posts` scan: Index Scan — `idx_posts_user`
- Join strategy: Nested Loop

---

## 2. Explore Feed

**Source:** `FeedJpaRepository.findExploreFeed`
**Tables:** `posts`, `follows` (subquery)

### Query plan
```
Limit  (cost=682.90..682.95 rows=20 width=265) (actual time=102.579..102.591 rows=20 loops=1)
  Buffers: shared hit=6 read=376
  ->  Sort  (cost=682.90..695.40 rows=5000 width=265) (actual time=102.576..102.580 rows=20 loops=1)
        Sort Key: ((p.like_count + p.comment_count)) DESC, p.created_at DESC
        Sort Method: top-N heapsort  Memory: 45kB
        Buffers: shared hit=6 read=376
        ->  Seq Scan on posts p  (cost=16.35..549.85 rows=5000 width=265) (actual time=7.539..85.337 rows=9996 loops=1)
"              Filter: ((deleted_at IS NULL) AND (NOT (hashed SubPlan 1)) AND (user_id <> '00000000-0000-0001-0000-000000000012'::uuid))"
              Rows Removed by Filter: 4
              Buffers: shared read=376
              SubPlan 1
                ->  Index Scan using idx_follows_follower on follows f  (cost=0.29..16.34 rows=3 width=16) (actual time=3.125..5.228 rows=3 loops=1)
"                      Index Cond: (follower_id = '00000000-0000-0001-0000-000000000012'::uuid)"
                      Filter: is_approved
                      Buffers: shared read=5
Planning:
  Buffers: shared hit=215 read=20
Planning Time: 35.600 ms
Execution Time: 104.091 ms
```

### Observations
- Execution Time: 104.09 1ms
- Planning Time: 35.600 ms
- `posts` scan: Seq Scan
- Subquery strategy: NOT IN 
- Notable:

---

## 3. Trending Hashtags

**Source:** `FeedJpaRepository.findTrendingHashtags`
**Tables:** `hashtags`, `hashtag_stats`

### Query plan
```
Limit  (cost=37.38..37.40 rows=10 width=51) (actual time=3.533..4.108 rows=10 loops=1)
  Buffers: shared hit=4 read=5
  ->  Sort  (cost=37.38..38.63 rows=500 width=51) (actual time=3.530..4.103 rows=10 loops=1)
        Sort Key: hs.weekly_count DESC
        Sort Method: top-N heapsort  Memory: 27kB
        Buffers: shared hit=4 read=5
        ->  Hash Join  (cost=15.25..26.57 rows=500 width=51) (actual time=1.980..3.381 rows=500 loops=1)
              Hash Cond: (h.id = hs.hashtag_id)
              Buffers: shared hit=4 read=5
              ->  Seq Scan on hashtags h  (cost=0.00..10.00 rows=500 width=39) (actual time=0.029..0.655 rows=500 loops=1)
                    Buffers: shared hit=2 read=3
              ->  Hash  (cost=9.00..9.00 rows=500 width=28) (actual time=1.417..1.418 rows=500 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 40kB
                    Buffers: shared hit=2 read=2
                    ->  Seq Scan on hashtag_stats hs  (cost=0.00..9.00 rows=500 width=28) (actual time=0.045..0.757 rows=500 loops=1)
                          Buffers: shared hit=2 read=2
Planning:
  Buffers: shared hit=182 read=19
Planning Time: 31.008 ms
Execution Time: 4.786 ms
```

### Observations
- Execution Time: 4.786 ms
- Planning Time: 31.008 ms
- Join strategy: HashJoin

---

## 4. Notifications List

**Source:** `NotificationJpaRepository.findByRecipientIdOrderByCreatedAtDesc`
**Tables:** `notifications`

### Query plan
```
Limit  (cost=0.29..8.30 rows=1 width=85) (actual time=2.932..2.941 rows=1 loops=1)
  Buffers: shared read=3
  ->  Index Scan using idx_notifications_recipient on notifications  (cost=0.29..8.30 rows=1 width=85) (actual time=2.927..2.934 rows=1 loops=1)
"        Index Cond: (recipient_id = '00000000-0000-0001-0000-000000000012'::uuid)"
        Buffers: shared read=3
Planning:
  Buffers: shared hit=62 read=5
Planning Time: 6.320 ms
Execution Time: 2.984 ms
```

### Observations
- Execution Time: ms
- Planning Time: ms
- Scan type: Index Scan — `idx_notifications_recipient`

---

## 5. RBAC Permissions *(fires on every request)*

**Source:** `UserRoleJpaRepository.findPermissionNamesByUserId`
**Tables:** `user_roles`, `roles`, `role_permissions`, `permissions`

### Query plan
```
Nested Loop  (cost=4.58..7.06 rows=6 width=14) (actual time=1.878..1.881 rows=0 loops=1)
  Join Filter: (ur.role_id = r.id)
  Buffers: shared read=4
  ->  Nested Loop  (cost=4.45..6.38 rows=1 width=46) (actual time=1.877..1.879 rows=0 loops=1)
        Buffers: shared read=4
        ->  Hash Join  (cost=4.32..5.61 rows=1 width=48) (actual time=1.876..1.878 rows=0 loops=1)
              Hash Cond: (rp.role_id = ur.role_id)
              Buffers: shared read=4
              ->  Seq Scan on role_permissions rp  (cost=0.00..1.23 rows=23 width=32) (actual time=0.555..0.559 rows=23 loops=1)
                    Buffers: shared read=1
              ->  Hash  (cost=4.30..4.30 rows=1 width=16) (actual time=1.302..1.302 rows=1 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared read=3
                    ->  Index Only Scan using user_roles_pkey on user_roles ur  (cost=0.29..4.30 rows=1 width=16) (actual time=1.289..1.293 rows=1 loops=1)
"                          Index Cond: (user_id = '00000000-0000-0001-0000-000000000012'::uuid)"
                          Heap Fetches: 0
                          Buffers: shared read=3
        ->  Index Scan using permissions_pkey on permissions p  (cost=0.14..0.67 rows=1 width=30) (never executed)
              Index Cond: (id = rp.permission_id)
  ->  Index Only Scan using roles_pkey on roles r  (cost=0.13..0.67 rows=1 width=16) (never executed)
        Index Cond: (id = rp.role_id)
        Heap Fetches: 0
Planning:
  Buffers: shared hit=142 read=10
Planning Time: 12.634 ms
Execution Time: 1.931 ms
```

### Observations
- Execution Time: 1.931 ms
- Planning Time: 12.634 ms
- Join strategy: NEST LOOP and HASH JOIN
- `user_roles` scan: INDEX SCAN - `user_roles_pkey`

---

## 6. User Profile Lookup

**Source:** `UserJpaRepository.findByUsername`
**Tables:** `users`

### Query plan
```
Index Scan using users_username_key on users  (cost=0.29..8.30 rows=1 width=854) (actual time=11.181..11.193 rows=1 loops=1)
"  Index Cond: (username = 'seed_user_12'::citext)"
  Buffers: shared read=3
Planning:
  Buffers: shared hit=154 read=7
Planning Time: 10.390 ms
Execution Time: 29.466 ms
```

### Observations
- Execution Time: 29.466 ms
- Planning Time: 10.390 ms
- Scan type: Index Scan — `idx_users_username`

---

## 7. User's Posts

**Source:** `PostJpaRepository.findByUserIdAndStatusNot`
**Tables:** `posts`

### Query plan
```
Limit  (cost=0.29..8.30 rows=1 width=261) (actual time=1.522..1.528 rows=1 loops=1)
  Buffers: shared hit=1 read=2
  ->  Index Scan using idx_posts_user on posts  (cost=0.29..8.30 rows=1 width=261) (actual time=1.518..1.523 rows=1 loops=1)
"        Index Cond: (user_id = '00000000-0000-0001-0000-000000000012'::uuid)"
"        Filter: (status <> 'DELETED'::post_status)"
        Buffers: shared hit=1 read=2
Planning:
  Buffers: shared hit=3
Planning Time: 0.382 ms
Execution Time: 1.560 ms
```

### Observations
- Execution Time: 1.560 ms
- Planning Time: 0.382 ms
- Scan type: Index Scan  — `idx_posts_user`

---

## 8. Post Media (batch)

**Source:** `PostMediaJpaRepository.findByPostIdIn`
**Tables:** `post_media`

### Query plan
```
Sort  (cost=32.57..32.58 rows=5 width=150) (actual time=2.071..2.074 rows=5 loops=1)
  Sort Key: sort_order
  Sort Method: quicksort  Memory: 25kB
  Buffers: shared hit=14 read=3
  ->  Index Scan using idx_post_media_post on post_media  (cost=0.29..32.51 rows=5 width=150) (actual time=1.920..1.960 rows=5 loops=1)
"        Index Cond: (post_id = ANY ('{00000000-0000-0001-0001-000000000001,00000000-0000-0001-0001-000000000002,00000000-0000-0001-0001-000000000003,00000000-0000-0001-0001-000000000004,00000000-0000-0001-0001-000000000005}'::uuid[]))"
        Buffers: shared hit=11 read=3
Planning:
  Buffers: shared hit=58 read=6
Planning Time: 10.843 ms
Execution Time: 2.129 ms
```

### Observations
- Execution Time: 2.129 ms
- Planning Time: 10.843 ms
- Scan type: Index Scan — `idx_post_media_post`

---

## 9. Comments — Top-level

**Source:** `CommentJpaRepository.findTopLevelByPostId`
**Tables:** `comments`

### Query plan
```
Limit  (cost=0.29..8.30 rows=1 width=113) (actual time=1.307..1.315 rows=1 loops=1)
  Buffers: shared hit=1 read=3
  ->  Index Scan Backward using idx_comments_post on comments c  (cost=0.29..8.30 rows=1 width=113) (actual time=1.304..1.311 rows=1 loops=1)
"        Index Cond: (post_id = '00000000-0000-0001-0001-000000000012'::uuid)"
        Filter: (parent_id IS NULL)
        Buffers: shared hit=1 read=3
Planning:
  Buffers: shared hit=84 read=6
Planning Time: 11.155 ms
Execution Time: 1.351 ms
```

### Observations
- Execution Time: 1.351 ms
- Planning Time: 11.155 ms
- Scan type: Index Scan  — `idx_comments_post`

---

## 10. Comment Replies

**Source:** `CommentJpaRepository.findRepliesByParentId`
**Tables:** `comments`

### Query plan
```
Limit  (cost=8.30..8.31 rows=1 width=113) (actual time=2.621..2.624 rows=1 loops=1)
  Buffers: shared read=3
  ->  Sort  (cost=8.30..8.31 rows=1 width=113) (actual time=2.619..2.620 rows=1 loops=1)
        Sort Key: created_at
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared read=3
        ->  Index Scan using idx_comments_parent on comments c  (cost=0.28..8.29 rows=1 width=113) (actual time=1.841..1.847 rows=1 loops=1)
"              Index Cond: (parent_id = '00000000-0000-0001-0002-000000000001'::uuid)"
              Filter: (NOT is_deleted)
              Buffers: shared read=3
Planning Time: 0.186 ms
Execution Time: 2.663 ms
```

### Observations
- Execution Time: 0.186 ms
- Planning Time: 2.663 ms
- Scan type: Index Scan — `idx_comments_parent`

---

## 11. Post Likers

**Source:** `PostLikeJpaRepository.findByIdPostIdOrderByCreatedAtDesc`
**Tables:** `post_likes`

### Query plan
```
Limit  (cost=8.31..8.32 rows=1 width=40) (actual time=2.193..2.196 rows=1 loops=1)
  Buffers: shared read=3
  ->  Sort  (cost=8.31..8.32 rows=1 width=40) (actual time=2.190..2.191 rows=1 loops=1)
        Sort Key: created_at DESC
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared read=3
        ->  Index Scan using idx_post_likes_post on post_likes  (cost=0.29..8.30 rows=1 width=40) (actual time=2.165..2.173 rows=1 loops=1)
"              Index Cond: (post_id = '00000000-0000-0001-0001-000000000012'::uuid)"
              Buffers: shared read=3
Planning:
  Buffers: shared hit=38 read=2
Planning Time: 16.509 ms
Execution Time: 2.242 ms
```

### Observations
- Execution Time: 2.242 ms
- Planning Time: 16.509 ms
- Scan type: Index Scan — `idx_post_likes_post`

---

## 12. Conversations List

**Source:** `ConversationJpaRepository.findByMemberId`
**Tables:** `conversations`, `conversation_members`

### Query plan
```
Limit  (cost=16.62..16.62 rows=1 width=109) (actual time=2.348..2.351 rows=1 loops=1)
  Buffers: shared hit=2 read=4
  ->  Sort  (cost=16.62..16.62 rows=1 width=109) (actual time=2.345..2.346 rows=1 loops=1)
        Sort Key: c.updated_at DESC
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared hit=2 read=4
        ->  Nested Loop  (cost=0.57..16.61 rows=1 width=109) (actual time=2.323..2.330 rows=1 loops=1)
              Buffers: shared hit=2 read=4
              ->  Index Scan using idx_conv_members_user on conversation_members m  (cost=0.29..8.30 rows=1 width=16) (actual time=1.479..1.484 rows=1 loops=1)
"                    Index Cond: (user_id = '00000000-0000-0001-0000-000000000012'::uuid)"
                    Buffers: shared read=3
              ->  Index Scan using conversations_pkey on conversations c  (cost=0.28..8.30 rows=1 width=109) (actual time=0.830..0.830 rows=1 loops=1)
                    Index Cond: (id = m.conversation_id)
                    Buffers: shared hit=2 read=1
Planning:
  Buffers: shared hit=79 read=13
Planning Time: 14.298 ms
Execution Time: 2.405 ms
```

### Observations
- Execution Time: 2.405 ms
- Planning Time: 14.298 ms
- `conversation_members` scan: Index Scan — `idx_conv_members_user`
- Join strategy:

---

## 13. Messages — First Page

**Source:** `MessageJpaRepository.findByConversationIdOrderByCreatedAtDesc`
**Tables:** `messages`

### Query plan
```
Limit  (cost=0.29..11.76 rows=2 width=160) (actual time=2.111..2.808 rows=2 loops=1)
  Buffers: shared read=4
  ->  Index Scan using idx_messages_conv on messages  (cost=0.29..11.76 rows=2 width=160) (actual time=2.106..2.800 rows=2 loops=1)
"        Index Cond: (conversation_id = '00000000-0000-0001-0004-000000000001'::uuid)"
        Buffers: shared read=4
Planning:
  Buffers: shared hit=72 read=4
Planning Time: 16.433 ms
Execution Time: 2.843 ms
```

### Observations
- Execution Time: 2.843 ms
- Planning Time: 16.433 ms
- Scan type: Index Scan — `idx_messages_conv`

---

## 14. Messages — Cursor-based

**Source:** `MessageJpaRepository.findOlderThanCursor`
**Tables:** `messages`

### Query plan
```
Limit  (cost=8.59..16.61 rows=1 width=160) (actual time=1.557..1.559 rows=0 loops=1)
  Buffers: shared hit=3 read=2
  InitPlan 1 (returns $0)
    ->  Index Scan using messages_pkey on messages m2  (cost=0.29..8.30 rows=1 width=8) (actual time=1.440..1.445 rows=1 loops=1)
"          Index Cond: (id = '00000000-0000-0001-0005-000000000002'::uuid)"
          Buffers: shared hit=1 read=2
  ->  Index Scan using idx_messages_conv on messages m  (cost=0.29..8.30 rows=1 width=160) (actual time=1.556..1.556 rows=0 loops=1)
"        Index Cond: ((conversation_id = '00000000-0000-0001-0004-000000000001'::uuid) AND (created_at < $0))"
        Buffers: shared hit=3 read=2
Planning Time: 0.241 ms
Execution Time: 1.633 ms
```

### Observations
- Execution Time: 1.633 ms
- Planning Time: 0.241 ms
- Subquery strategy: InitPlan
- Scan type: Index Scan

---

## 15. Unread Message Count

**Source:** `MessageReadJpaRepository.countUnread`
**Tables:** `messages`, `message_reads` (correlated subquery)

### Query plan
```
Aggregate  (cost=28.38..28.39 rows=1 width=8) (actual time=0.864..0.865 rows=1 loops=1)
  Buffers: shared hit=6 read=2
  ->  Index Scan using idx_messages_conv on messages m  (cost=0.29..28.38 rows=1 width=184) (actual time=0.816..0.831 rows=2 loops=1)
"        Index Cond: (conversation_id = '00000000-0000-0001-0004-000000000001'::uuid)"
"        Filter: ((sender_id <> '00000000-0000-0001-0000-000000000012'::uuid) AND (created_at > COALESCE((SubPlan 1), '1970-01-01 00:00:00+00'::timestamp with time zone)))"
        Buffers: shared hit=6 read=2
        SubPlan 1
          ->  Index Scan using message_reads_pkey on message_reads r  (cost=0.29..8.30 rows=1 width=8) (actual time=0.366..0.366 rows=0 loops=2)
"                Index Cond: ((message_id = m.id) AND (user_id = '00000000-0000-0001-0000-000000000012'::uuid))"
                Buffers: shared hit=2 read=2
Planning:
  Buffers: shared hit=30 read=1
Planning Time: 3.190 ms
Execution Time: 1.151 ms
```

### Observations
- Execution Time: 1.151 ms
- Planning Time: 3.190 ms
- Correlated subquery fires per row: yes because is use the SubPlan for the SubQueryStrategy
- Notable: COALESCE

---

## 16. Followers List

**Source:** `FollowJpaRepository.findByIdFollowingIdAndIsApproved`
**Tables:** `follows`

### Query plan
```
Limit  (cost=127.32..127.37 rows=20 width=41) (actual time=31.542..31.551 rows=20 loops=1)
  Buffers: shared hit=2 read=45
  ->  Sort  (cost=127.32..127.44 rows=47 width=41) (actual time=31.539..31.545 rows=20 loops=1)
        Sort Key: created_at DESC
        Sort Method: top-N heapsort  Memory: 27kB
        Buffers: shared hit=2 read=45
        ->  Bitmap Heap Scan on follows  (cost=4.68..126.07 rows=47 width=41) (actual time=3.349..30.588 rows=51 loops=1)
"              Recheck Cond: (following_id = '00000000-0000-0001-0000-000000000001'::uuid)"
              Filter: is_approved
              Heap Blocks: exact=45
              Buffers: shared hit=2 read=45
              ->  Bitmap Index Scan on idx_follows_following  (cost=0.00..4.67 rows=51 width=0) (actual time=2.095..2.096 rows=51 loops=1)
"                    Index Cond: (following_id = '00000000-0000-0001-0000-000000000001'::uuid)"
                    Buffers: shared read=2
Planning:
  Buffers: shared hit=3
Planning Time: 5.533 ms
Execution Time: 32.110 ms
```

### Observations
- Execution Time: 32.110 ms
- Planning Time: 5.533 ms
- Scan type: Index Scan  — `idx_follows_following`

---

## 17. Following List

**Source:** `FollowJpaRepository.findByIdFollowerIdAndIsApproved`
**Tables:** `follows`

### Query plan
```
Limit  (cost=15.32..15.33 rows=3 width=41) (actual time=4.442..4.445 rows=3 loops=1)
  Buffers: shared hit=5
  ->  Sort  (cost=15.32..15.33 rows=3 width=41) (actual time=4.440..4.442 rows=3 loops=1)
        Sort Key: created_at DESC
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared hit=5
        ->  Bitmap Heap Scan on follows  (cost=4.31..15.29 rows=3 width=41) (actual time=3.843..3.852 rows=3 loops=1)
"              Recheck Cond: (follower_id = '00000000-0000-0001-0000-000000000012'::uuid)"
              Filter: is_approved
              Heap Blocks: exact=3
              Buffers: shared hit=5
              ->  Bitmap Index Scan on idx_follows_follower  (cost=0.00..4.31 rows=3 width=0) (actual time=3.312..3.313 rows=3 loops=1)
"                    Index Cond: (follower_id = '00000000-0000-0001-0000-000000000012'::uuid)"
                    Buffers: shared hit=2
Planning Time: 0.129 ms
Execution Time: 4.482 ms
```

### Observations
- Execution Time: 4.482 ms
- Planning Time: 0.129 ms
- Scan type: Index Scan  — `idx_follows_follower`

---

## 18. Saved Posts

**Source:** `SavedPostJpaRepository.findByIdUserIdOrderBySavedAtDesc`
**Tables:** `saved_posts`

### Query plan
```
Limit  (cost=0.29..8.30 rows=1 width=258) (actual time=1.508..1.515 rows=1 loops=1)
  Buffers: shared read=3
  ->  Index Scan using idx_saved_posts_user on saved_posts  (cost=0.29..8.30 rows=1 width=258) (actual time=1.505..1.510 rows=1 loops=1)
"        Index Cond: (user_id = '00000000-0000-0001-0000-000000000012'::uuid)"
        Buffers: shared read=3
Planning:
  Buffers: shared hit=44 read=2
Planning Time: 7.190 ms
Execution Time: 1.545 ms
```

### Observations
- Execution Time: 1.545 ms
- Planning Time: 7.190 ms
- Scan type: Index Scan — `idx_saved_posts_user`

---

## 19. Block Filter — Blocked IDs

**Source:** `UserBlockJpaRepository.findBlockedIdsByBlockerId`
**Tables:** `user_blocks`

### Query plan
```
Index Only Scan using user_blocks_pkey on user_blocks  (cost=0.28..4.29 rows=1 width=16) (actual time=1.508..1.513 rows=1 loops=1)
"  Index Cond: (blocker_id = '00000000-0000-0001-0000-000000000012'::uuid)"
  Heap Fetches: 0
  Buffers: shared read=3
Planning:
  Buffers: shared hit=34 read=3
Planning Time: 6.818 ms
Execution Time: 1.542 ms
```

### Observations
- Execution Time: 1.542 ms
- Planning Time: 6.818 ms
- Scan type: Index Scan

---

## 20. Block Filter — Blocker IDs

**Source:** `UserBlockJpaRepository.findBlockerIdsByBlockedId`
**Tables:** `user_blocks`

### Query plan
```
Index Scan using idx_blocks_blocked on user_blocks  (cost=0.28..8.29 rows=1 width=16) (actual time=1.056..1.058 rows=0 loops=1)
"  Index Cond: (blocked_id = '00000000-0000-0001-0000-000000000012'::uuid)"
  Buffers: shared read=2
Planning Time: 0.089 ms
Execution Time: 1.084 ms
```

### Observations
- Execution Time: 1.084 ms
- Planning Time: 0.089 ms
- Scan type: Index Scan / Seq Scan — `idx_blocks_blocked`

---

## 21. Search Users

**Source:** `UserJpaRepository.findByUsernameContainingIgnoreCase`
**Tables:** `users`

### Query plan
```
Limit  (cost=28.64..58.73 rows=10 width=854) (actual time=0.091..0.093 rows=1 loops=1)
  Buffers: shared hit=12
  ->  Bitmap Heap Scan on users  (cost=28.64..176.10 rows=49 width=854) (actual time=0.090..0.091 rows=1 loops=1)
"        Recheck Cond: (search_tsv @@ to_tsquery('seed_user_5'::text))"
"        Filter: (account_status = 'ACTIVE'::account_status)"
        Heap Blocks: exact=1
        Buffers: shared hit=12
        ->  Bitmap Index Scan on idx_users_search_fts  (cost=0.00..28.62 rows=50 width=0) (actual time=0.074..0.074 rows=1 loops=1)
"              Index Cond: (search_tsv @@ to_tsquery('seed_user_5'::text))"
              Buffers: shared hit=11
Planning:
  Buffers: shared hit=1
Planning Time: 0.195 ms
Execution Time: 0.130 ms
```

### Observations
- Execution Time: 0.130 ms
- Planning Time: 0.195 ms
- Scan type: Seq Scan — `idx_users_search_fts` (GIN trgm)

---

## 22. Hashtag Posts

**Source:** `SearchJpaAdapter.findPostsByHashtag`
**Tables:** `posts`, `post_hashtags`, `hashtags`

### Query plan
```
Limit  (cost=69.96..70.01 rows=20 width=261) (actual time=1.300..1.302 rows=0 loops=1)
  Buffers: shared read=2
  ->  Sort  (cost=69.96..70.01 rows=20 width=261) (actual time=1.298..1.300 rows=0 loops=1)
        Sort Key: p.created_at DESC
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared read=2
        ->  Nested Loop  (cost=5.00..69.53 rows=20 width=261) (actual time=1.289..1.291 rows=0 loops=1)
              Buffers: shared read=2
              ->  Nested Loop  (cost=4.71..60.18 rows=20 width=16) (actual time=1.289..1.290 rows=0 loops=1)
                    Buffers: shared read=2
                    ->  Index Scan using hashtags_name_key on hashtags h  (cost=0.27..8.29 rows=1 width=16) (actual time=1.288..1.288 rows=0 loops=1)
"                          Index Cond: (name = 'photography_0'::citext)"
                          Buffers: shared read=2
                    ->  Bitmap Heap Scan on post_hashtags ph  (cost=4.44..51.69 rows=20 width=32) (never executed)
                          Recheck Cond: (hashtag_id = h.id)
                          ->  Bitmap Index Scan on idx_post_hashtags_hashtag  (cost=0.00..4.44 rows=20 width=0) (never executed)
                                Index Cond: (hashtag_id = h.id)
              ->  Index Scan using posts_pkey on posts p  (cost=0.29..0.47 rows=1 width=261) (never executed)
                    Index Cond: (id = ph.post_id)
                    Filter: (deleted_at IS NULL)
Planning:
  Buffers: shared hit=66 read=13
Planning Time: 28.292 ms
Execution Time: 1.615 ms
```

### Observations
- Execution Time: 1.615 ms
- Planning Time: 28.292 ms
- Join strategy: Nest Loop
- Scan type: Index Scan, Bitmap Index Scan

---

## After TASK-10.3 — Redis Cache Added

<!-- Fill in after completing TASK-10.3 -->

| Query | Before (Postgres ms) | After (Redis ms) | Speedup |
|---|---|---|---|
| Home Feed | | | |
| Explore Feed | | | |
| Notifications | | | |

---

## After TASK-10.7 — Index Audit

<!-- Fill in after completing TASK-10.7 -->

| Query | Before (ms) | After (ms) | Plan change |
|---|---|---|---|
| | | | Seq Scan → Index Scan |
