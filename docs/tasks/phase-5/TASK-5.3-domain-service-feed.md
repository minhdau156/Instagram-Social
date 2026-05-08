# TASK-5.3 — Domain Service: FeedService

## Overview

Implement `FeedService` — the domain service that orchestrates home and explore feed assembly. It depends only on the `FeedRepository` out-port and implements both in-ports from TASK-5.2.

## Requirements

- Annotated with `@Service`.
- Constructor injection only — all dependencies are `final`.
- Never references JPA or persistence classes directly.
- Home feed: chronological posts from followed users, keyset-paginated.
- Explore feed: engagement-ranked posts excluding followed users.

## File Location

```
backend/src/main/java/com/instagram/domain/service/FeedService.java
```

## Dependencies

| Field | Interface | Purpose |
|-------|-----------|---------|
| `feedRepository` | `FeedRepository` | Fetch home/explore feed posts and trending hashtags |

---

## Checklist

- [x] Create `FeedService.java` annotated with `@Service`
- [x] Add constructor accepting `FeedRepository feedRepository`
- [x] Declare field `private final FeedRepository feedRepository`
- [x] Implement `GetHomeFeedUseCase`
- [x] Implement `GetExploreFeedUseCase`

### `getHomeFeed(Query query)` implementation

```java
@Override
public GetHomeFeedUseCase.FeedPage getHomeFeed(GetHomeFeedUseCase.Query query) {
    // TODO: add Redis cache for cursor=null (page 1) after Phase 10
    List<Post> posts = feedRepository.getHomeFeed(
            query.userId(), query.cursor(), query.limit());

    UUID nextCursor = posts.size() < query.limit()
            ? null
            : posts.get(posts.size() - 1).getId();

    return new GetHomeFeedUseCase.FeedPage(posts, nextCursor);
}
```

### `getExploreFeed(Query query)` implementation

```java
@Override
public GetExploreFeedUseCase.FeedPage getExploreFeed(GetExploreFeedUseCase.Query query) {
    List<Post> posts = feedRepository.getExploreFeed(
            query.userId(), query.cursor(), query.limit());

    UUID nextCursor = posts.size() < query.limit()
            ? null
            : posts.get(posts.size() - 1).getId();

    return new GetExploreFeedUseCase.FeedPage(posts, nextCursor);
}
```

## Notes

- `nextCursor` logic: if the repository returned fewer items than the requested `limit`, there are no more pages → return `null`. Otherwise return the last item's id.
- The `// TODO` comment for Redis caching is **intentional** — leave it as a marker for Phase 10. Do not implement caching now.
- `FeedService` does **not** check follow visibility or post ownership — that is the persistence adapter's responsibility.
