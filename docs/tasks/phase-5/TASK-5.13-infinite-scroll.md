# TASK-5.13 — InfiniteScroll Utility Component

## Overview

Create a reusable `InfiniteScroll` wrapper component that uses the browser's `IntersectionObserver` API to detect when the user has scrolled to the bottom of a list and automatically triggers the next page fetch.

## Requirements

- Lives in `frontend/src/components/common/`.
- Takes `fetchNextPage`, `hasNextPage`, `isFetchingNextPage`, and `children` as props.
- Places an invisible sentinel `div` at the bottom; when it enters the viewport, calls `fetchNextPage`.
- Shows `SkeletonList` (or a passed-in loader) while `isFetchingNextPage` is true.
- Works for both `HomePage` and `ExplorePage` — generic, not feed-specific.

## File Location

```
frontend/src/components/common/InfiniteScroll.tsx
```

---

## Checklist

### Props interface

- [ ] Define the props type:

  ```typescript
  interface InfiniteScrollProps {
    /** Call this when the sentinel enters the viewport */
    fetchNextPage: () => void;
    /** False when the last page has nextCursor = null */
    hasNextPage: boolean;
    /** True while the next page is being fetched */
    isFetchingNextPage: boolean;
    /** The already-loaded content to display above the sentinel */
    children: React.ReactNode;
    /** Optional custom loader shown at the bottom. Defaults to a simple CircularProgress. */
    loader?: React.ReactNode;
  }
  ```

### Component implementation

- [ ] Create `InfiniteScroll.tsx`:

  ```tsx
  import { useEffect, useRef } from 'react';
  import { Box, CircularProgress } from '@mui/material';

  export function InfiniteScroll({
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
    children,
    loader,
  }: InfiniteScrollProps) {
    const sentinelRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
      const sentinel = sentinelRef.current;
      if (!sentinel) return;

      const observer = new IntersectionObserver(
        (entries) => {
          // When the sentinel is visible and there are more pages, fetch
          if (entries[0].isIntersecting && hasNextPage && !isFetchingNextPage) {
            fetchNextPage();
          }
        },
        {
          // Start loading slightly before the user reaches the very bottom
          rootMargin: '200px',
        }
      );

      observer.observe(sentinel);

      // Cleanup when component unmounts or deps change
      return () => observer.disconnect();
    }, [fetchNextPage, hasNextPage, isFetchingNextPage]);

    return (
      <>
        {children}

        {/* Sentinel: invisible div that triggers loading when visible */}
        <div ref={sentinelRef} style={{ height: 1 }} />

        {/* Loading indicator shown at the bottom of the list */}
        {isFetchingNextPage && (
          <Box display="flex" justifyContent="center" py={3}>
            {loader ?? <CircularProgress size={28} />}
          </Box>
        )}
      </>
    );
  }
  ```

### Usage example (for your reference — do not create this file)

```tsx
// In HomePage:
<InfiniteScroll
  fetchNextPage={fetchNextPage}
  hasNextPage={hasNextPage}
  isFetchingNextPage={isFetchingNextPage}
>
  {posts.map((post) => (
    <PostCard key={post.id} post={post} />
  ))}
</InfiniteScroll>
```

## Notes

- `rootMargin: '200px'` means the observer fires 200px before the sentinel reaches the viewport, giving the network request time to complete before the user actually hits the bottom. Tune this value if needed.
- The `useEffect` dependency array includes `fetchNextPage` — React Query returns a stable reference for this function, so it will not cause unnecessary re-subscriptions.
- Do NOT use a library like `react-infinite-scroll-component` — the project aims to keep dependencies minimal and this implementation is ~30 lines.
