# TASK-5.17 — Register Routes: `/` and `/explore`

## Overview

Wire up the two new Phase 5 pages into the router. The home feed (`/`) should already be defined as a route — verify and update it to use `HomePage`. Add `/explore` as a new protected route pointing to `ExplorePage`.

## Requirements

- Routes are registered in `frontend/src/App.tsx` (or wherever the project's `<Routes>` block lives — check the existing file).
- Both routes are protected (redirect to `/login` if the user is not authenticated).
- Lazy-load both pages with `React.lazy` to keep the initial bundle small.

## File to Modify

```
frontend/src/App.tsx
```

---

## Checklist

### Step 1 — Verify the router file

- [ ] Open `frontend/src/App.tsx` (or `frontend/src/router.tsx` if routes are in a separate file).
- [ ] Find the existing `<Routes>` block and locate where protected routes are defined.
- [ ] Check if there is already a `path="/"` route — if it points to a placeholder or old component, update it.

### Step 2 — Add lazy imports

- [ ] Add lazy imports near the top of the router file (alongside other lazy imports):

  ```tsx
  const HomePage    = React.lazy(() => import('./pages/feed/HomePage'));
  const ExplorePage = React.lazy(() => import('./pages/explore/ExplorePage'));
  ```

### Step 3 — Register routes

- [ ] Inside the protected routes section, add or update:

  ```tsx
  <Route path="/"        element={<HomePage />} />
  <Route path="/explore" element={<ExplorePage />} />
  ```

  > If the project uses a `<ProtectedRoute>` wrapper component, wrap these routes with it. Check how `/saved` or `/profile` routes are registered in `TASK-4.35` for the exact pattern to follow.

### Step 4 — Verify navigation links

- [ ] Confirm the `AppShell` or sidebar navigation has a link to `/explore`:
  - Open `frontend/src/AppShell.tsx` (or the sidebar/nav component).
  - Find the navigation item list.
  - Check if an "Explore" link already exists — if not, add one:

    ```tsx
    <ListItemButton component={RouterLink} to="/explore">
      <ListItemIcon><ExploreIcon /></ListItemIcon>
      <ListItemText primary="Explore" />
    </ListItemButton>
    ```

  - The home (feed) link pointing to `/` should already exist.

### Step 5 — Smoke test

- [ ] Start the frontend dev server (`npm run dev`).
- [ ] Navigate to `http://localhost:5173/` — verify the home feed renders (even if the backend is not running, the loading/error states should appear).
- [ ] Navigate to `http://localhost:5173/explore` — verify the explore page renders.
- [ ] Navigate to both pages while unauthenticated — verify redirect to `/login`.

## Notes

- If the project does not use `React.lazy` yet for any other routes, it is safe to add it here — it requires `<Suspense fallback={<Loading />}>` to wrap the lazy routes. Check if there is already a `Suspense` boundary in `App.tsx`. If not, wrap the `<Routes>` block:

  ```tsx
  <Suspense fallback={<CircularProgress />}>
    <Routes>
      {/* ... */}
    </Routes>
  </Suspense>
  ```
- Do not remove existing routes — this task only adds/updates two routes.
