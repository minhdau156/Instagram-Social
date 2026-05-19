# TASK-8.15 — Register Routes & Integrate SearchBar into Navigation

## Overview

Register two new protected routes (`/search` and `/hashtag/:name`) in the app router, and integrate the `SearchBar` component into the existing navigation (sidebar or app bar). Follow the exact pattern used in Phase 7 for notification routes.

## Requirements

- Both routes must be protected — wrap with the existing `ProtectedRoute` component.
- Use `React.lazy` + `Suspense` for lazy loading (code-splitting).
- Both page files must use `export default` — `React.lazy` requires it (confirm before registering).
- `SearchBar` placement depends on the current navigation layout — read `AppShell.tsx` before deciding where to add it.

## Files to Modify

```
frontend/src/App.tsx          (add routes)
frontend/src/AppShell.tsx     (add SearchBar to navigation)
```

---

## Instructions

### Step 1 — Lazy-import the two pages at the top of `App.tsx`

Add two `React.lazy` declarations alongside the other lazy-imported pages:

```ts
const SearchPage   = React.lazy(() => import('./pages/search/SearchPage'));
const HashtagPage  = React.lazy(() => import('./pages/search/HashtagPage'));
```

**What `React.lazy` does:** It defers loading the page's JavaScript bundle until the user first navigates to that route. Vite sees the dynamic `import()` inside `React.lazy` and creates a separate chunk for each page. This keeps the initial bundle small — the search code is only downloaded when the user actually visits `/search` or `/hashtag/:name`. `React.lazy` requires the target module to have a `default` export; a named export causes a runtime error.

### Step 2 — Add routes inside the router in `App.tsx`

Find where the existing protected routes are declared. Look for `<ProtectedRoute>` usages — specifically the notification routes added in TASK-7.21 or the messaging routes from TASK-6.21. Add the new routes following the exact same structure:

```tsx
<Route
  path="/search"
  element={
    <ProtectedRoute>
      <Suspense fallback={<LoadingScreen />}>
        <SearchPage />
      </Suspense>
    </ProtectedRoute>
  }
/>
<Route
  path="/hashtag/:name"
  element={
    <ProtectedRoute>
      <Suspense fallback={<LoadingScreen />}>
        <HashtagPage />
      </Suspense>
    </ProtectedRoute>
  }
/>
```

Use whatever `fallback` component the other lazy routes already use (`LoadingScreen`, `CircularProgress`, etc.) — keep it consistent with the existing routes.

**Why `path="/hashtag/:name"`:** The `:name` segment is a URL parameter captured by `useParams` inside `HashtagPage`. The hashtag name is embedded in the path (e.g., `/hashtag/travel`) rather than a query string (e.g., `/hashtag?name=travel`) because path parameters are more idiomatic for resource identifiers and are better handled by browser history.

### Step 3 — Integrate `SearchBar` into `AppShell.tsx`

Read `AppShell.tsx` first to understand the current navigation structure before making changes.

**Likely scenarios:**

**Scenario A — Sidebar navigation (desktop layout):**
The project likely has a left-side navigation drawer (consistent with Instagram's layout). Place `SearchBar` near the top of the sidebar, above the nav links:
- Import `SearchBar` from `'./components/search/SearchBar'`.
- Add it inside the sidebar `Box`, above the first `NavLink` or icon row.
- Pass `onNavigate={() => { /* close mobile drawer if open */ }}` if the sidebar is collapsible on mobile.
- The `SearchBar` should have `fullWidth` behaviour within the sidebar's padding.

**Scenario B — App bar (top bar layout):**
If the app uses a top `AppBar` layout:
- Import `SearchBar`.
- Add it in the centre section of the `AppBar` (between the logo and the action icons).
- Constrain its width: `sx={{ maxWidth: 300, flex: 1 }}`.

**In both scenarios:**
- Do not duplicate `SearchBar` in both locations — pick one based on the existing layout.
- Confirm the import path is correct relative to `AppShell.tsx`.

### Step 4 — Add a "Search" navigation link (optional but recommended)

If `AppShell.tsx` has a list of navigation links (e.g., Home, Explore, Messages, Notifications), add a "Search" link pointing to `/search`:

```tsx
<NavLink to="/search" style={({ isActive }) => ({ /* active styles */ })}>
  <SearchIcon />
  <span>Search</span>
</NavLink>
```

This gives users a way to reach the full `SearchPage` (with tabs) independent of the `SearchBar` dropdown.

## Notes

- Confirm both `SearchPage` and `HashtagPage` start with `export default function` before registering the routes. A named export will cause a silent runtime failure — `React.lazy` resolves the module but finds no default export.
- The `path="/hashtag/:name"` route must be distinct from any other route patterns. Check that no existing route uses `/hashtag` as a prefix.
- After adding the `SearchBar` to `AppShell.tsx`, test that it renders without errors by running `npm run dev` and visually verifying the layout. Check that the dropdown appears below the input and that keyboard navigation works.
- The `SearchBar` calls `useSearch` and `useSearchHistory` internally — both make API calls. Confirm the authenticated Axios instance is properly configured (JWT attached via interceptor) so these requests succeed without 401 errors.
