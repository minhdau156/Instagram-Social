# Cache Candidates

Identified by scanning all 15 application service classes. Methods ranked by read frequency and implementation priority.

---

## Tier 1 — Critical

High-read, low-write paths where caching has the greatest ROI.

### FeedService.getHomeFeed()

- **Cache name:** `feed`
- **Key:** `feed:{userId}:page1`
- **TTL:** 60 s
- **Condition:** `cursor == null` only (first page)
- **Evict on:** new post created, follow/unfollow
- **Why:** Loaded on every app open. Runs a multi-join query (`posts` × `follows`) plus N batch media queries and N per-post like checks.

### UserService.getUserProfile()

- **Cache name:** `profile`
- **Key:** `profile:{username}`
- **TTL:** 5 min
- **Evict on:** `updateProfile()`
- **Why:** 3 separate queries per visit (user row, follower/following stats, follow-state check). Viewed on every profile page, story tap, and mention.

### UserService.getUserStats()

- **Cache name:** `userStats`
- **Key:** `userStats:{userId}`
- **TTL:** 5 min
- **Evict on:** follow, unfollow, createPost, deletePost
- **Why:** Follower count, following count, and post count are rendered in every user card and profile header — extremely hot read path.

### RbacService.getUserPermissions()

- **Cache name:** `userPermissions`
- **Key:** `userPermissions:{userId}`
- **TTL:** 1–4 h
- **Evict on:** `assignRole()`, `revokeRole()`, `updateRolePermissions()`
- **Why:** Evaluated on **every authenticated request** via `@PreAuthorize`. Data changes almost never. Caching this alone eliminates a DB round-trip from every API call.

### PostService.getPostMedia()

- **Cache name:** `postMedia`
- **Key:** `postMedia:{postId}`
- **TTL:** 1 h (effectively permanent — media is immutable once uploaded)
- **Evict on:** never
- **Why:** Loaded with every post render. Media files do not change after upload; a long TTL is safe.

### MessagingService.getConversations()

- **Cache name:** `conversations`
- **Key:** `conversations:{userId}`
- **TTL:** 2–5 min
- **Evict on:** `sendMessage()`, new conversation created
- **Why:** Loaded on every app launch. Requires 3+ queries (conversation list, latest message per conversation, batch user details).

---

## Tier 2 — High

Worth caching after Tier 1 is stable.

### FeedService.getExploreFeed()

- **Cache name:** `exploreFeed`
- **Key:** `exploreFeed:{userId}:page1`
- **TTL:** 10–15 min
- **Condition:** `cursor == null` only
- **Evict on:** follow change, new post (approximate — TTL expiry is acceptable)
- **Why:** Complex subquery aggregating like + comment counts across posts not from followed users.


### PostService.getUserPosts()

- **Cache name:** `userPosts`
- **Key:** `userPosts:{userId}:page{page}`
- **TTL:** 30 min
- **Evict on:** `createPost()`, `deletePost()`
- **Why:** Profile grid; loaded on every profile visit. Changes only when the user creates or deletes a post.

### CommentService.getComments()

- **Cache name:** `comments`
- **Key:** `comments:{postId}:page{page}`
- **TTL:** 5–10 min
- **Evict on:** `addComment()`, `editComment()`, `deleteComment()`
- **Why:** 2 queries + N per-comment like checks (N+1 risk). Loaded every time a post is expanded.

### FollowService.getFollowers() / getFollowing()

- **Cache name:** `followers` / `following`
- **Key:** `followers:{userId}:page{page}` / `following:{userId}:page{page}`
- **TTL:** 15 min
- **Evict on:** `follow()`, `unfollow()`, `approveFollowRequest()`, `declineFollowRequest()`
- **Why:** 3 queries each (IDs page, batch user load, current-user follow flags). Loaded every time a user opens the followers/following sheet.


---

## Tier 3 — Medium

Consider once Tier 1 and 2 are in place.

| Method | Cache name | Key pattern | TTL | Evict on |
|---|---|---|---|---|

| `SavedPostService.getSavedPosts()` | `savedPosts` | `savedPosts:{userId}:page{page}` | 15 min | `save()`, `unsave()` |
| `FollowService.getFollowRequests()` | `followRequests` | `followRequests:{userId}` | 10 min | new follow request, approve/decline |
| `NotificationService.getSettings()` | `notifSettings` | `notifSettings:{userId}` | 1 h | `updateNotificationSettings()` |
| `RbacService.getUserRoles()` | `userRoles` | `userRoles:{userId}` | 2 h | `assignRole()`, `revokeRole()` |
| `RbacService.listRoles()` | `roles` | `roles:all` | 4 h | `updateRolePermissions()` |

---

## Do Not Cache

| Method | Reason |
|---|---|
| `NotificationService.getNotifications()` | High write frequency (notifications created and marked-read constantly); cache would invalidate before providing value — optimize the query instead |
| `AdminService.*` | Admin operations require up-to-the-second consistency |
| `ModerationService.getReports()` | Same as admin; stale data is a moderation risk |
| `MessagingService.getMessages()` | Active threads are write-heavy; cache only older pages (large cursor offset) if needed |

---

## Implementation Notes

- **Fix N+1 before caching:** `getPost`, `getComments`, and `getReplies` each run N per-row like/save checks. Batch those into a single `IN` query first; otherwise you cache a slow result rather than a fast one.
- **User-scoped keys:** Always include `userId` in the key for any data that differs per viewer (like state, follow state, permissions).
- **Cursor-based feeds:** Cache only `cursor == null` (page 1). Deep-page cursors are unique per session and would fill Redis with entries that are never reused.
- **Serialization:** Domain objects use hand-written builders with no no-arg constructor. Prefer caching response DTOs (e.g., `UserProfileResponse`) to avoid Jackson deserialization issues with `GenericJackson2JsonRedisSerializer`.
- **Redis must be running:** `spring-data-redis` attempts to connect on startup. Use `spring.cache.type=none` in a no-Redis profile to keep the app startable without Docker.
