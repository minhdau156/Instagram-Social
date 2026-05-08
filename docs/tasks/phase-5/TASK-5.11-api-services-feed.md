# TASK-5.11 — API Service: feedApi.ts

## Overview

Create `feedApi.ts` — a single API service module for all three feed endpoints. Uses the shared `api` axios instance from `./client`. Follows the same pattern as `postApi.ts` and `followApi.ts`.

## Requirements

- Lives in `frontend/src/api/`.
- Uses the `api` instance from `./client` (already configured with base URL and JWT interceptor).
- All functions are `async` and return properly typed data.
- No business logic — pure HTTP calls.
- Unwraps the `data.data` envelope (the backend wraps responses in `{ data: ..., error: null }`).

## File Location

```
frontend/src/api/feedApi.ts
```

---

## Checklist

- [ ] Create `feedApi.ts` with three exported functions:

  ```typescript
  import { api } from './client';
  import type { FeedPage } from '../types/post';
  import type { TrendingHashtag } from '../types/hashtag';

  /**
   * Fetches the authenticated user's home feed.
   * cursor: UUID string of last seen post (omit for first page).
   */
  export async function getHomeFeed(cursor?: string): Promise<FeedPage> {
    const params: Record<string, string | number> = { limit: 20 };
    if (cursor) params.cursor = cursor;

    const { data } = await api.get('/api/v1/feed', { params });
    return data.data as FeedPage;
  }

  /**
   * Fetches the explore feed (posts not from followed users, ranked by engagement).
   * cursor: UUID string of last seen post (omit for first page).
   */
  export async function getExploreFeed(cursor?: string): Promise<FeedPage> {
    const params: Record<string, string | number> = { limit: 20 };
    if (cursor) params.cursor = cursor;

    const { data } = await api.get('/api/v1/explore', { params });
    return data.data as FeedPage;
  }

  /**
   * Fetches the top trending hashtags (max 10 by default).
   */
  export async function getTrendingHashtags(limit = 10): Promise<TrendingHashtag[]> {
    const { data } = await api.get('/api/v1/explore/hashtags', {
      params: { limit },
    });
    return data.data as TrendingHashtag[];
  }
  ```

- [ ] Verify `FeedPage` is exported from `../types/post` (added in TASK-5.10).
- [ ] Verify `TrendingHashtag` is exported from `../types/hashtag` (created in TASK-5.10).

## Notes

- The `limit` is hardcoded to `20` in the API calls — this is intentional. If the UI ever needs a different page size, add it as a parameter then.
- The `as FeedPage` cast is safe here because the backend contract guarantees the shape. If runtime validation is needed later, use Zod.
