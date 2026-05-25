# TASK-9.18 — Register Routes

## Overview

Register all Phase 9 routes in `App.tsx` and integrate moderation entry points into the existing navigation in `AppShell.tsx`. This task also adds the "Blocked Accounts" link to the settings menu, and exposes "Report" and "Block" controls in the appropriate kebab menus.

---

## Requirements

- All new page components must use `export default function` — confirm before registering (a named export causes a silent runtime failure with `React.lazy`).
- Use `React.lazy` + `Suspense` for code-splitting all new page components.
- Admin pages must be wrapped with `AdminRoute` in addition to the standard `ProtectedRoute`.
- The fallback component used in `Suspense` must match the one already in use by other lazy routes — check the existing routes before deciding.

---

## Files to Modify

```
frontend/src/App.tsx        ← add all new routes
frontend/src/AppShell.tsx   ← add settings link + report/block trigger points
```

---

## Checklist

### Step 1 — Lazy-import all new pages at the top of `App.tsx`

- [x] Add `React.lazy` declarations for the four new page components:
  - `BlockedAccountsPage` from `'./pages/settings/BlockedAccountsPage'`
  - `AdminDashboardPage` from `'./pages/admin/AdminDashboardPage'`
  - `AdminReportsPage` from `'./pages/admin/AdminReportsPage'`
  - `AdminUsersPage` from `'./pages/admin/AdminUsersPage'`
- [x] Confirm that the import paths match the actual file locations created in TASK-9.15 and TASK-9.16 exactly (including casing — Windows is case-insensitive but the CI build may not be).

---

### Step 2 — Register user-facing settings route in `App.tsx`

- [x] Find the section in `App.tsx` where other `/settings/` routes are registered (e.g., `/settings/notifications` from TASK-7.21). Add the new route immediately after:
  - Path: `/settings/blocked`
  - Element: `BlockedAccountsPage` wrapped in `ProtectedRoute` and `Suspense`. Follow the exact same nesting pattern used for `/settings/notifications`.

---

### Step 3 — Register admin routes in `App.tsx`

- [x] Find the section where protected routes are declared. Add the four admin routes after the existing protected routes:
  - Path: `/admin` — element: `AdminDashboardPage` wrapped with `AdminRoute` inside `Suspense`.
  - Path: `/admin/reports` — element: `AdminReportsPage` wrapped with `AdminRoute` inside `Suspense`.
  - Path: `/admin/users` — element: `AdminUsersPage` wrapped with `AdminRoute` inside `Suspense`.
- [x] Do not wrap admin routes with `ProtectedRoute` if `AdminRoute` already handles the unauthenticated redirect (see TASK-9.17 Notes). If you chose to nest `ProtectedRoute` inside `AdminRoute`, add it here. Document the nesting decision in a comment next to the routes.
- [x] Confirm that no existing route uses `/admin` or `/admin/*` as a prefix. If a conflict exists, resolve it before adding the new routes.

---

### Step 4 — Add "Blocked Accounts" to the settings navigation in `AppShell.tsx`

- [x] Open `AppShell.tsx` and locate the settings navigation items or the settings menu. Phase 7 added a "Notification Settings" link (`/settings/notifications`) — follow the same pattern.
- [x] Add a `NavLink` (or equivalent menu item) for `/settings/blocked` with label "Blocked Accounts" and `BlockIcon` from `@mui/icons-material/Block`.
- [x] This link must only appear in the settings menu — not in the main navigation bar.

---

### Step 5 — Add "Report" trigger to existing PostCard (integration point)

- [x] Open `frontend/src/components/posts/PostCard.tsx` (or the equivalent post card component). Locate the kebab menu (the `IconButton` with `MoreVertIcon` that opens a `Menu`).
- [x] Import `ReportDialog` from `'../moderation/ReportDialog'`.
- [x] Add a `MenuItem` labelled "Report" to the kebab menu. This item should only appear when the post does NOT belong to the current user (do not allow self-reporting).
- [x] Add local state `reportDialogOpen: boolean` and wire the `MenuItem onClick` to open the dialog.
- [x] Render `<ReportDialog open={reportDialogOpen} onClose={() => setReportDialogOpen(false)} entityType="POST" entityId={post.id} title="Report this post" />` alongside the `PostCard` JSX (outside the `Menu`).

---

### Step 6 — Add "Block" / "Unblock" trigger to user profile (integration point)

- [x] Open `frontend/src/pages/profile/ProfilePage.tsx` (or the equivalent user profile page). Locate the "more options" menu or the profile action area.
- [x] Import `BlockButton` from `'../../components/moderation/BlockButton'`.
- [x] Add `BlockButton` to the profile action area (alongside the existing Follow/Unfollow button). Pass `username={profileUser.username}` and `isBlocked={isBlocked}` where `isBlocked` is derived from the user relationship state already available on the profile page (check whether the existing Phase 3 follow state also includes a block indicator — if not, add a `useQuery(['is-blocked', username], () => moderationApi.isBlocked(username))` call to fetch the block state when the profile loads, OR pre-fetch it as part of the profile response if the backend includes it).
- [x] The `BlockButton` must not appear on the current user's own profile — guard with `if (profileUser.id !== currentUserId)`.

---

### Step 7 — Verify all routes render without errors

- [x] Start the frontend development server (`npm run dev`).
- [x] Navigate to `/settings/blocked` as a logged-in user — confirm `BlockedAccountsPage` renders.
- [x] Navigate to `/admin` as a `ROLE_USER` account — confirm redirect to `/` and toast appears.
- [x] Navigate to `/admin` as a `ROLE_ADMIN` account — confirm `AdminDashboardPage` renders.
- [x] Navigate to `/admin/reports` — confirm `AdminReportsPage` renders.
- [x] Navigate to `/admin/users` — confirm `AdminUsersPage` renders.
- [x] Open a post card and click the kebab menu — confirm "Report" option is present for other users' posts.
- [x] Open a user profile and confirm "Block" button is present for other users' profiles.

---

## Notes

- The `/admin` root route must not conflict with any existing route. Check the full route list in `App.tsx` before adding.
- `AdminRoute` wraps the page element, not the entire `Route` tree. Each admin route has its own individual `AdminRoute` wrapper — there is no shared layout route for all admin pages at this stage. A shared `AdminLayout` (with sidebar navigation) can be added in Phase 10.
- The `ReportDialog` and `BlockButton` wiring steps (Steps 5 and 6) modify existing Phase 2 and Phase 3 components. These are surgical changes — add only the dialog state, import, and menu item. Do not refactor, reformat, or clean up other parts of those component files.
- If the profile page does not yet have a mechanism to determine whether the viewing user has blocked the profile user, the simplest approach for Phase 9 is to call `moderationApi.getBlockedUsers()` (already called for the settings page) and derive `isBlocked` by checking whether the profile user's ID is in the returned list. Cache this under the `['blocked-users']` query key (already populated by `useBlockedUsers`).
