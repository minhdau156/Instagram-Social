# TASK-5.10 — TypeScript Types: FeedPage & TrendingHashtag

## Overview

Extend the existing `post.ts` types file with a `FeedPage` type (keyset-cursor based, different from the existing offset-based `PostPage`), and create a new `hashtag.ts` file for the trending hashtag type.

## Requirements

- `FeedPage` uses `nextCursor: string | null` (a UUID string) — **not** page/size/totalPages.
- `TrendingHashtag` is a new file because hashtags are a separate domain concept from posts.
- No `any` types. Strict TypeScript.

## File Locations

```
frontend/src/types/post.ts     ← add FeedPage (edit existing file)
frontend/src/types/hashtag.ts  ← new file
```

---

## Checklist

### Update `frontend/src/types/post.ts`

- [x] Add `FeedPage` interface at the bottom of the file:

  ```typescript
  /**
   * Keyset-paginated feed response.
   * nextCursor is the UUID of the last post in this page,
   * or null when there are no more pages.
   */
  export interface FeedPage {
    posts: Post[];
    nextCursor: string | null;
  }
  ```

  > Do NOT modify the existing `PostPage` interface — it is used by profile and post-list endpoints that use offset pagination.

---

### Create `frontend/src/types/hashtag.ts`

- [x] Create the file:

  ```typescript
  export interface TrendingHashtag {
    id: string;
    name: string;       // without '#' prefix — add '#' in the UI
    postCount: number;
  }
  ```

## Notes

- `FeedPage` intentionally omits `totalElements` and `totalPages` — the backend does not compute them for keyset-paginated feeds (it would require a full COUNT scan).
- `nextCursor` being `string | null` (not `string | undefined`) makes it easy to pass directly to `useInfiniteQuery`'s `getNextPageParam` — return `undefined` (not `null`) to signal "no more pages" since React Query treats `undefined` as the stop signal.
