# TASK-5.12 — Custom Hooks: useHomeFeed & useExploreFeed

## Overview

Create two `useInfiniteQuery` hooks that handle paginated data fetching for the home and explore feeds. These hooks power the infinite-scroll behaviour in `HomePage` and `ExplorePage`.

## Requirements

- Lives in `frontend/src/hooks/`.
- Uses `@tanstack/react-query` v5 — specifically `useInfiniteQuery`.
- `getNextPageParam` converts the backend's `nextCursor` string into the next page's cursor argument.
- Refetch on window focus with 60-second `staleTime` (keeps the feed fresh without hammering the backend).
- Returns a typed result that components can destructure without casting.

## File Locations

```
frontend/src/hooks/
├── useHomeFeed.ts
└── useExploreFeed.ts
```

---

## Checklist

### `useHomeFeed.ts`

- [ ] Create the hook:

  ```typescript
  import { useInfiniteQuery } from '@tanstack/react-query';
  import { getHomeFeed } from '../api/feedApi';
  import type { FeedPage } from '../types/post';

  export function useHomeFeed() {
    return useInfiniteQuery<FeedPage, Error, FeedPage, string[], string | undefined>({
      queryKey: ['homeFeed'],
      queryFn: ({ pageParam }) => getHomeFeed(pageParam),

      // pageParam is the cursor from the previous page's response.
      // undefined means "first page" (no cursor).
      initialPageParam: undefined,

      // Return undefined to stop fetching when there's no more cursor.
      getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,

      staleTime: 60_000,        // 60 seconds — don't refetch if data is fresh
      refetchOnWindowFocus: true,
    });
  }
  ```

- [ ] Verify import path for `getHomeFeed` and `FeedPage`.

**What the hook returns (key fields components use):**
| Field | Type | Use |
|-------|------|-----|
| `data.pages` | `FeedPage[]` | Flatten with `.flatMap(p => p.posts)` to get all loaded posts |
| `fetchNextPage` | `() => void` | Call when the scroll sentinel is visible |
| `hasNextPage` | `boolean` | `true` when `nextCursor` is not null |
| `isFetchingNextPage` | `boolean` | Show skeleton at the bottom while loading more |
| `isLoading` | `boolean` | True only on the very first load |
| `isError` | `boolean` | Show error state |

---

### `useExploreFeed.ts`

- [ ] Create the hook using the **exact same pattern** as `useHomeFeed`, with these differences:
  - `queryKey: ['exploreFeed']`
  - `queryFn: ({ pageParam }) => getExploreFeed(pageParam)`
  - `staleTime: 120_000` (explore content changes less frequently — 2 min is fine)

  ```typescript
  import { useInfiniteQuery } from '@tanstack/react-query';
  import { getExploreFeed } from '../api/feedApi';
  import type { FeedPage } from '../types/post';

  export function useExploreFeed() {
    return useInfiniteQuery<FeedPage, Error, FeedPage, string[], string | undefined>({
      queryKey: ['exploreFeed'],
      queryFn: ({ pageParam }) => getExploreFeed(pageParam),
      initialPageParam: undefined,
      getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
      staleTime: 120_000,
      refetchOnWindowFocus: false, // explore doesn't need live updates
    });
  }
  ```

## Notes

- The generic type arguments `useInfiniteQuery<TData, TError, TSelectData, TQueryKey, TPageParam>` are verbose but avoid `any`. In React Query v5, the 5th generic is the page param type — use `string | undefined` because the cursor is a UUID string or `undefined` for the first page.
- `data.pages` is an array of `FeedPage` objects — one per loaded page. To render all posts, flatten: `data.pages.flatMap(page => page.posts)`.
- Never store the posts in local state — always derive them from `data.pages`. React Query owns the cache.
