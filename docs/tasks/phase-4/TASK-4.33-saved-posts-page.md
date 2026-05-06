# TASK-4.33 — Saved Posts Page

## Overview

Create `SavedPostsPage` — a protected page displaying the authenticated user's saved (bookmarked) posts in a grid layout. This reuses the existing `PostGrid` component.

## Requirements

- Lives in `frontend/src/pages/profile/`.
- Protected route — redirect to `/login` if not authenticated.
- Uses MUI components and follows the existing page structure (header, content area).
- Reuses the existing `PostGrid` component (or equivalent post grid layout from Phase 2).
- Handles loading, error, and empty states.

## File Location

```
frontend/src/pages/profile/SavedPostsPage.tsx
```

---

## Checklist

- [x] Create `SavedPostsPage.tsx`:

  ```tsx
  export default function SavedPostsPage() {
    const { user } = useAuth();
    const { data, isLoading, isError, fetchNextPage, hasNextPage, isFetchingNextPage } =
      useInfiniteQuery({
        queryKey: ['savedPosts'],
        queryFn: ({ pageParam = 0 }) => getSavedPosts(pageParam, 20),
        getNextPageParam: (lastPage) => lastPage.last ? undefined : lastPage.page + 1,
        initialPageParam: 0,
      });

    // ... render
  }
  ```

- [x] Import `getSavedPosts` from `../api/savesApi`

- [x] **Loading state**: Show MUI `Skeleton` grid (3-column, 9 skeleton cells) while initial data is loading

- [x] **Error state**: Show MUI `Alert` with severity `"error"` and a retry button

- [x] **Empty state**: Show centred icon (`BookmarkBorderIcon`, large), heading `"No saved posts yet"`, and subtext `"Tap the bookmark icon on any post to save it here."`

- [x] **Content**: Render posts in a 3-column responsive grid:
  - Extract `postId` values from `data.pages.flatMap(p => p.content)`
  - For each `postId`, render a `PostThumbnail` component (fetch individual post details via existing `getPostById` API or display from cache)
  - Alternatively, the backend `GET /users/me/saved` can be enhanced in a follow-up to return enriched post data instead of just `SavedPost` records

- [x] **Page header**: `Typography` with `"Saved"` as the page title

- [x] **Infinite scroll sentinel**: Use `IntersectionObserver` at the bottom of the list to call `fetchNextPage()`

- [x] **Protected route check**: Use `useAuth()` hook — if `user` is null, render `<Navigate to="/login" replace />`

- [x] This route will be registered as `/saved` in TASK-4.35
