# TASK-8.7 — DTOs: Request & Response

## Overview

Create response DTOs for the search feature. All DTOs are Java records with static `from(DomainModel)` factory methods. There are no request DTOs — all search inputs come via query parameters, not request bodies.

## Requirements

- Java records only.
- Static `from(...)` factory methods mapping from domain models.
- Lives in `adapter/in/web/dto/`.
- No Lombok needed — records are already concise.

## File Locations

```
backend/src/main/java/com/instagram/adapter/in/web/dto/UserSearchResponse.java
backend/src/main/java/com/instagram/adapter/in/web/dto/HashtagSearchResponse.java
backend/src/main/java/com/instagram/adapter/in/web/dto/PostSearchResponse.java
backend/src/main/java/com/instagram/adapter/in/web/dto/SearchHistoryResponse.java
```

---

## Checklist

### `UserSearchResponse.java`

- [ ] Record fields:
  - `String id` — user UUID as string
  - `String username`
  - `String fullName`
  - `String avatarUrl` — nullable; frontend must handle null
  - `boolean isPrivate`
  - `int followerCount`
- [ ] `public static UserSearchResponse from(User user)` factory method mapping all fields.
- [ ] Note: do NOT include sensitive fields (`email`, `passwordHash`, etc.). Only include fields the frontend needs to render a search result row (avatar, username, display name, follower count, privacy flag).

### `HashtagSearchResponse.java`

- [ ] Record fields:
  - `String id` — hashtag UUID as string
  - `String name` — the hashtag name without the `#` prefix (matches the DB column)
  - `int postCount` — denormalized from the `hashtags.post_count` column
- [ ] `public static HashtagSearchResponse from(Hashtag hashtag)` factory method.

### `PostSearchResponse.java`

- [ ] Record fields:
  - `String id` — post UUID as string
  - `String authorUsername`
  - `String authorAvatarUrl` — nullable
  - `String caption` — nullable; may be null for photo-only posts
  - `String mediaUrl` — the first media item's URL (thumbnail for the search result tile)
  - `String mediaType` — `"IMAGE"` or `"VIDEO"`
  - `int likeCount`
  - `int commentCount`
  - `String createdAt` — ISO 8601 string
- [ ] `public static PostSearchResponse from(Post post)` factory method.
  - For `authorUsername` and `authorAvatarUrl`: the domain `Post` model may not carry the author's username directly (it carries `userId`). Check `Post.java`. If the author info is absent, the controller must resolve it via an additional `GetUserUseCase` call. Document this dependency in a comment in the `from` method or add a second overload `from(Post post, User author)`.
  - For `mediaUrl` / `mediaType`: take the first element of `post.getMediaItems()` (or equivalent field) if the Post model stores a list, or use the primary media URL if it is a single field.

### `SearchHistoryResponse.java`

- [ ] Record fields:
  - `String id`
  - `String query`
  - `String searchedAt` — ISO 8601 string
- [ ] `public static SearchHistoryResponse from(SearchHistory entry)` factory method.
- [ ] Note: there is no `searchType` field — the schema does not store it (see TASK-8.1 schema verification note).

## Notes

- `PostSearchResponse.from(Post)` may require the author's `User` object if `Post` only stores `UUID authorId`. In that case, declare a second factory: `public static PostSearchResponse from(Post post, User author)`. The controller (TASK-8.6) is responsible for batch-fetching authors before mapping — follow the same batch-fetch pattern used in `NotificationController`.
- Keep DTOs in the `dto/` sub-package, never in the controller file itself.
- Do not reuse `PostResponse` from Phase 2 — `PostSearchResponse` is a lighter projection intended for search result grids (no full comment list, no save status, etc.).
