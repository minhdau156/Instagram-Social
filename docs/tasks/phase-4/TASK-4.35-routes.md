# TASK-4.35 — Register Routes

## Overview

Register the new page routes for Phase 4 social interactions in the application's router configuration. This is the final wiring step that makes the new pages accessible via URL navigation.

## Requirements

- Updates the existing `App.tsx` (or `router.tsx` if routes are extracted) — no new files.
- New routes must be protected where required (authenticated users only).
- Follow the existing pattern for route registration (consistent with TASK-3.20).

## File Location

```
frontend/src/App.tsx    (or frontend/src/router.tsx if extracted)
```

---

## Checklist

- [x] Import `SavedPostsPage`:
  ```typescript
  import SavedPostsPage from './pages/profile/SavedPostsPage';
  ```

- [x] Register protected route `/saved`:
  ```tsx
  <Route
    path="/saved"
    element={
      <ProtectedRoute>
        <SavedPostsPage />
      </ProtectedRoute>
    }
  />
  ```
  - Place this route alongside other protected routes (e.g., after the `/follow-requests` route from Phase 3).

- [x] Verify `ProtectedRoute` component exists and redirects unauthenticated users to `/login` — it should have been created in Phase 3 (TASK-3.20). If not, create a simple wrapper:
  ```tsx
  function ProtectedRoute({ children }: { children: React.ReactNode }) {
    const { user } = useAuth();
    if (!user) return <Navigate to="/login" replace />;
    return <>{children}</>;
  }
  ```

- [x] Add a navigation link to `/saved` in the sidebar or bottom navigation (if a global `Sidebar.tsx` or `BottomNav.tsx` component exists):
  ```tsx
  <NavLink to="/saved" aria-label="Saved posts">
    <BookmarkBorderIcon />
  </NavLink>
  ```
  - Show filled `BookmarkIcon` when the route is active (`isActive` from React Router `NavLink`)

- [x] Verify that the `/p/:postId` route (for shareable post links referenced in `ShareMenu`) either already exists or add it:
  ```tsx
  <Route path="/p/:postId" element={<PostDetailPage />} />
  ```
  - `PostDetailPage` can render `PostDetailModal` in full-page mode or redirect to the feed with the modal open.
  - If this page does not exist yet, create a minimal `PostDetailPage.tsx` that fetches the post by ID and renders `PostDetailModal`.

- [x] After all route changes, verify the router renders correctly by checking:
  - [x] `http://localhost:5173/saved` loads `SavedPostsPage` for authenticated users
  - [x] `http://localhost:5173/saved` redirects to `/login` for unauthenticated users
  - [x] `http://localhost:5173/p/<some-post-id>` loads the post detail
