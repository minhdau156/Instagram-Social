# N+1 Query Issues

All confirmed N+1 problems found across service and persistence layers.

---

## Issue 1 — Lazy `@ManyToOne` user load in feed mapping

**File:** `adapter/out/persistence/FeedJpaQueryAdapter.java:111`
**Triggered by:** `getHomeFeed()` and `getExploreFeed()`

**Problem:**
`PostJpaEntity.user` is `@ManyToOne(fetch = FetchType.LAZY)`. Calling `entity.getUser().getId()` inside `.stream().map(this::toDomain)` fires one `SELECT` per post.

```java
// toDomain() called once per post in the stream
.userId(entity.getUser().getId())  // triggers lazy SELECT per post
```

**Fix:** Add a JPQL fetch join in `FeedJpaRepository` to eagerly load the user in a single query:
```java
@Query("SELECT p FROM PostJpaEntity p JOIN FETCH p.user WHERE ...")
```
Or add `@EntityGraph(attributePaths = "user")` to the repository method.

---

## Issue 2 — Like check per post in home feed

**File:** `application/service/FeedService.java:45-48`
**Method:** `getHomeFeed()`

**Problem:**
`likeRepository.hasLikedPost()` is called once per post inside a loop — N posts = N extra queries.

```java
for (Post post : posts) {
    boolean hasLiked = likeRepository.hasLikedPost(post.getId(), query.userId()); // N queries
    post.setLikedByCurrentUser(hasLiked);
}
```

**Fix:** Add a batch method to `LikeRepository`:
```java
Set<UUID> findLikedPostIdsByUserIdAndPostIds(UUID userId, List<UUID> postIds);
```
Then replace the loop:
```java
List<UUID> postIds = posts.stream().map(Post::getId).toList();
Set<UUID> likedIds = likeRepository.findLikedPostIdsByUserIdAndPostIds(query.userId(), postIds);
posts.forEach(p -> p.setLikedByCurrentUser(likedIds.contains(p.getId())));
```

---

## Issue 3 — Like check per comment in getComments

**File:** `application/service/CommentService.java:192`
**Method:** `getComments()`

**Problem:**
`likeRepository.hasLikedComment()` called once per comment inside `Page.map()`.

```java
return comments.map(comment -> {
    boolean isLikedByCurrentUser = this.likeRepository.hasLikedComment(comment.getId(), currentUser.getId()); // N+1
    ...
});
```

**Fix:** Add a batch method to `LikeRepository`:
```java
Set<UUID> findLikedCommentIdsByUserIdAndCommentIds(UUID userId, List<UUID> commentIds);
```
Load once before mapping, then use the set inside `.map()`.

---

## Issue 4 — Like check per reply in getReplies

**File:** `application/service/CommentService.java:76`
**Method:** `getReplies()`

**Problem:**
Same pattern as Issue 3 — `likeRepository.hasLikedComment()` called once per reply inside `Page.map()`.

```java
return comments.map(comment -> {
    boolean isLikedByCurrentUser = this.likeRepository.hasLikedComment(comment.getId(), currentUser.getId()); // N+1
    ...
});
```

**Fix:** Same batch approach as Issue 3. Extract comment IDs from the page, load liked IDs in one query, use the set inside `.map()`.

---

## Issue 5 — User lookup per @mention in addComment

**File:** `application/service/CommentService.java:162-175`
**Method:** `addComment()`

**Problem:**
One `SELECT` per mentioned username in a loop.

```java
for (String username : mentions) {
    userRepository.findByUsername(username)  // 1 query per mention
        .ifPresent(mentionedUser -> { ... });
}
```

**Fix:** Add a batch method to `UserRepository`:
```java
List<User> findByUsernames(List<String> usernames);
```
Then load all mentioned users in a single query:
```java
List<User> mentionedUsers = userRepository.findByUsernames(mentions);
mentionedUsers.forEach(mentionedUser -> { ... });
```

---

## Issue 6 — Last message lookup per conversation

**File:** `application/service/MessagingService.java:161-164`
**Method:** `getConversations()`

**Problem:**
`messageRepository.findLatestByConversationId()` called once per conversation.

```java
for (Conversation c : conversations) {
    messageRepository.findLatestByConversationId(c.getId())  // N queries
        .ifPresent(m -> lastMessages.put(c.getId(), m));
}
```

**Fix:** Add a batch method to `MessageRepository`:
```java
List<Message> findLatestByConversationIds(List<UUID> conversationIds);
```
Use a `ROW_NUMBER()` / `DISTINCT ON` query to fetch the latest message per conversation in one shot, then group by `conversationId` into a `Map<UUID, Message>`.

---

## Issue 7 — Member IDs lookup per 1-1 conversation

**File:** `application/service/MessagingService.java:168-175`
**Method:** `getConversations()`

**Problem:**
`conversationRepository.findMemberIds()` called once per non-group conversation.

```java
for (Conversation c : conversations) {
    if (!c.isGroup()) {
        conversationRepository.findMemberIds(c.getId())  // N queries
            .stream()
            .filter(id -> !id.equals(query.userId()))
            .findFirst()
            .ifPresent(otherId -> conversationToOtherMember.put(c.getId(), otherId));
    }
}
```

**Fix:** Add a batch method to `ConversationRepository`:
```java
Map<UUID, List<UUID>> findMemberIdsByConversationIds(List<UUID> conversationIds);
```
Fetch all member rows for all conversation IDs at once and group in Java.

---

## Issue 8 — Unread count per conversation in stream map

**File:** `application/service/MessagingService.java:203`
**Method:** `getConversations()`

**Problem:**
`messageRepository.getUnreadCount()` called once per conversation inside a `.map()`.

```java
return conversations.stream()
    .map(c -> new ConversationView(
        c,
        messageRepository.getUnreadCount(c.getId(), query.userId()),  // N+1
        ...
    ));
```

**Fix:** Add a batch method to `MessageRepository`:
```java
Map<UUID, Long> getUnreadCountsByConversationIds(List<UUID> conversationIds, UUID userId);
```
Load all counts in one query before the `.map()`.

> **Note (Issues 6, 7, 8 combined):** `getConversations()` with 20 conversations currently fires up to **60 extra queries**. All three should be fixed together.

---

## Issue 9 — Follow status per liker in getPostLikers

**File:** `application/service/LikeService.java:149-150`
**Method:** `getPostLikers()`

**Problem:**
`followRepository.findByFollowerIdAndFollowingId()` called once per liker inside `Page.map()`.

```java
Page<UserSummary> likers = postLikerIds.map(id -> {
    Optional<Follow> followOpt = followRepository.findByFollowerIdAndFollowingId(
        query.requestingUserId(), user.getId());  // 1 query per liker
    ...
});
```

**Fix:** Add a batch method to `FollowRepository`:
```java
Map<UUID, FollowStatus> findFollowStatusByFollowerIdAndFollowingIds(UUID followerId, List<UUID> followingIds);
```
Load all follow statuses for the page of likers in one query before `.map()`.

---

## Summary

| # | File | Method | Line(s) | Root Cause | Impact |
|---|------|--------|---------|-----------|--------|
| 1 | `FeedJpaQueryAdapter` | `getHomeFeed` / `getExploreFeed` | 111 | Lazy `@ManyToOne` user | 1 SELECT per post |
| 2 | `FeedService` | `getHomeFeed` | 45–48 | Loop: `hasLikedPost()` | 1 query per post |
| 3 | `CommentService` | `getComments` | 192 | Map: `hasLikedComment()` | 1 query per comment |
| 4 | `CommentService` | `getReplies` | 76 | Map: `hasLikedComment()` | 1 query per reply |
| 5 | `CommentService` | `addComment` | 162–175 | Loop: `findByUsername()` | 1 query per @mention |
| 6 | `MessagingService` | `getConversations` | 161–164 | Loop: `findLatestByConversationId()` | 1 query per conversation |
| 7 | `MessagingService` | `getConversations` | 168–175 | Loop: `findMemberIds()` | 1 query per 1-1 conversation |
| 8 | `MessagingService` | `getConversations` | 203 | Map: `getUnreadCount()` | 1 query per conversation |
| 9 | `LikeService` | `getPostLikers` | 149–150 | Map: `findByFollowerIdAndFollowingId()` | 1 query per liker |
