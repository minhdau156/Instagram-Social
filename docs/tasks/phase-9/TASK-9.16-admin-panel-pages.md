# TASK-9.16 — Admin Panel Pages: Dashboard, Reports, Users

## Overview

Create three admin-only pages and one shared admin dialog component. The admin panel lives under the `/admin` route prefix and is only accessible to users with `ROLE_ADMIN`. These pages are intentionally functional and data-dense — they are internal tools, not consumer-facing UI.

---

## Requirements

- All pages live in `frontend/src/pages/admin/`.
- All page components must use `export default function` for `React.lazy` compatibility.
- The `ReviewReportDialog` shared component lives in `frontend/src/components/admin/`.
- MUI and `sx` prop only for all styling.
- All data fetching via React Query hooks — no manual `useEffect` + `useState` for server state.

---

## File Locations

```
frontend/src/pages/admin/AdminDashboardPage.tsx
frontend/src/pages/admin/AdminReportsPage.tsx
frontend/src/pages/admin/AdminUsersPage.tsx
frontend/src/components/admin/ReviewReportDialog.tsx
```

---

## Checklist

### Custom hooks prerequisite

Create the following hooks in `frontend/src/hooks/admin/` before building the pages:

- [x] `useAdminReports.ts` — uses `useQuery` with key `['admin-reports', status, page]`. Calls `adminApi.getReports(status, page, size)`. Returns `{ reports, isLoading, isError }`.
- [x] `useReviewReport.ts` — uses `useMutation` calling `adminApi.reviewReport(id, payload)`. On success, invalidates `['admin-reports']` queries so the list refreshes.
- [x] `useAdminUsers.ts` — uses `useQuery` with key `['admin-users', filters, page]`. Calls `adminApi.getAdminUsers(filters, page, size)`. Returns `{ users, isLoading, isError }`.
- [x] `useSuspendUser.ts` — uses `useMutation` calling `adminApi.suspendUser(id, payload)`. On success, invalidates `['admin-users']`.
- [x] `useUnsuspendUser.ts` — uses `useMutation` calling `adminApi.unsuspendUser(id)`. On success, invalidates `['admin-users']`.

---

### `AdminDashboardPage.tsx`

#### Purpose

The landing page of the admin panel. Shows an at-a-glance summary of platform health metrics.

#### Layout

- [x] Page title: MUI `Typography variant="h4" fontWeight={700} mb={3}` — "Admin Dashboard".
- [x] Stats cards row: MUI `Grid container spacing={2}`. Render one `Grid item xs={12} sm={6} md={3}` per stat card. Each card is a MUI `Card` with a `CardContent` block containing:
  - `Typography variant="h3" fontWeight={700}` — the numeric value (e.g., total users count).
  - `Typography variant="body2" color="text.secondary"` — the label (e.g., "Total Users").
  - An icon from `@mui/icons-material` appropriate to the stat (e.g., `PeopleIcon`, `ArticleIcon`, `ReportIcon`, `BlockIcon`).
- [x] Stats to show (four cards):
  - **Pending Reports** — calls `adminApi.getReports('PENDING', 0, 1)` and uses the list length as a proxy count (or add a dedicated `countByStatus` API endpoint if available).
  - **Total Users** — calls `adminApi.getAdminUsers(undefined, 0, 0)` to get the user list (the total count, if the API returns it in a `totalElements` field, use that; otherwise display the current page count as a placeholder).
  - **Suspended Users** — calls `adminApi.getAdminUsers({ status: 'SUSPENDED' }, 0, 1)` for a count.
  - **Reports Resolved Today** — calls `adminApi.getReports('RESOLVED', 0, 100)` and filters client-side by `createdAt` being today's date. Note this is an approximation — a dedicated endpoint would be better but is out of scope.
- [x] Loading state for each card: show `Skeleton variant="rectangular" height={80}` while data is fetching.
- [x] Quick-action buttons below the stats row:
  - MUI `Button variant="contained"` — "Review Pending Reports" — navigates to `/admin/reports?status=PENDING`.
  - MUI `Button variant="outlined"` — "Manage Users" — navigates to `/admin/users`.

---

### `AdminReportsPage.tsx`

#### Purpose

Full table of content reports. Admins can filter by status and take action (resolve, dismiss, or mark as reviewed) on individual reports.

#### URL-driven state

- [x] Use `useSearchParams()` to read `status` from the URL. This allows deep-linking to filtered views (e.g., `/admin/reports?status=PENDING`).
- [x] Declare a tab bar at the top: "All" | "Pending" | "Reviewed" | "Resolved" | "Dismissed". Each tab updates the `status` URL param via `setSearchParams`.

#### Layout

- [x] Page title: `Typography variant="h5" fontWeight={600}` — "Content Reports".
- [x] MUI `Tabs` + `Tab` bar below the title. Tab values: `undefined` (All), `PENDING`, `REVIEWED`, `RESOLVED`, `DISMISSED`. The active tab is derived from the `status` URL param.
- [x] Data: call `useAdminReports(activeStatus, page, 20)`. Pass `activeStatus` as `undefined` when the "All" tab is active.

#### Reports table

- [x] MUI `TableContainer` with `Paper`:
  - `Table` with columns: Reporter, Entity Type, Reason, Status, Submitted Date, Actions.
  - `TableHead` with `TableRow` of `TableCell` headers.
  - `TableBody` with one `TableRow` per report.
- [x] Each data row (`TableRow`):
  - **Reporter** cell: `reporterUsername` — show as a plain text string.
  - **Entity Type** cell: a MUI `Chip` label set to `report.entityType` with a size-appropriate colour. Suggested colour scheme: `POST → primary`, `COMMENT → default`, `USER → secondary`, `MESSAGE → warning`.
  - **Reason** cell: `report.reason` — plain text.
  - **Status** cell: a MUI `Chip` with colour coding. Suggested: `PENDING → warning`, `REVIEWED → info`, `RESOLVED → success`, `DISMISSED → default`.
  - **Submitted Date** cell: formatted with `format(new Date(report.createdAt), 'MMM d, yyyy')` from `date-fns`.
  - **Actions** cell: a MUI `IconButton` with `MoreVertIcon` that opens a `Menu` with options: "Resolve", "Dismiss", "Mark as Reviewed". Each option opens `ReviewReportDialog` pre-filled with the chosen action.

#### Loading state

- [x] While `isLoading`: render 8 skeleton table rows using `Skeleton variant="rectangular" height={52}`.

#### Empty state

- [x] When the filtered list is empty: render a centred MUI `Box py={6}` with `Typography color="text.secondary"` — "No reports matching the current filter."

#### Pagination

- [x] Below the table: a `useState(0)` for `page`. Show MUI `Pagination` component with a `count` of `Math.ceil(totalCount / 20)`. Since the backend returns a list (not a total count), use a simple "previous / next" approach: disable "Next" when the list has fewer than 20 items, disable "Previous" when on page 0.

---

### `AdminUsersPage.tsx`

#### Purpose

A searchable, filterable table of all registered users. Admins can suspend or unsuspend individual users.

#### Layout

- [x] Page title: `Typography variant="h5" fontWeight={600}` — "User Management".
- [x] Filter bar above the table:
  - MUI `TextField size="small" placeholder="Search by username..."` — debounced with 300 ms `useState`+`useEffect` before triggering the query (same pattern as `useSearch` in Phase 8).
  - MUI `Select` for account status filter: options "All", "Active", "Suspended", "Deactivated".
  - These controls update local state that is passed to `useAdminUsers(filters, page, 20)`.

#### Users table

- [x] MUI `TableContainer` with `Paper`:
  - Columns: Avatar, Username, Email, Full Name, Status, Verified, Joined Date, Actions.
  - **Avatar** cell: small MUI `Avatar src={user.avatarUrl ?? undefined}` — note: `AdminUserResponse` may not carry `avatarUrl`. If it does not, show a generic person icon.
  - **Username** cell: `user.username` as a clickable link that navigates to `/profile/{user.username}` (so the admin can see the public profile).
  - **Email** cell: `user.email`.
  - **Status** cell: MUI `Chip` with colour coding: `ACTIVE → success`, `SUSPENDED → error`, `DEACTIVATED → default`, `PENDING_VERIFICATION → warning`.
  - **Verified** cell: `CheckCircleIcon color="primary"` if `isVerified`, otherwise a dash.
  - **Joined Date** cell: formatted `createdAt`.
  - **Actions** cell: `IconButton` with `MoreVertIcon` opening a MUI `Menu`:
    - "View Profile" — navigates to `/profile/{user.username}`.
    - If `status === 'ACTIVE'`: show "Suspend User" option. On click, open a MUI `Dialog` prompting for a reason (`TextField`) and confirm.
    - If `status === 'SUSPENDED'`: show "Unsuspend User" option. On click, call `unsuspendUser(user.id)` directly without a confirmation dialog.

#### Suspension confirmation dialog (inline)

- [x] Declare a local `suspendTarget: AdminUser | null` state. When the admin selects "Suspend User", set `suspendTarget`. Show a MUI `Dialog` when `suspendTarget !== null`:
  - Title: "Suspend @{suspendTarget.username}?".
  - Body: `TextField multiline rows={3} label="Reason" required` bound to a local `suspendReason` string state.
  - Actions: "Cancel" (clears `suspendTarget`) and "Suspend" button (calls `suspendUser(suspendTarget.id, { reason: suspendReason })` and clears `suspendTarget` on success).
  - Disable the "Suspend" button when `suspendReason.trim() === ''`.

#### Loading and empty states

- [x] Loading: 10 skeleton table rows.
- [x] Empty: `Typography color="text.secondary" align="center" py={4}` — "No users found matching the current filters."

---

### `ReviewReportDialog.tsx`

#### Purpose

A shared dialog used by `AdminReportsPage` to let an admin choose a review action for a specific report.

#### Props interface

- [x] `open: boolean`
- [x] `onClose: () => void`
- [x] `report: Report | null` — the report to review; `null` when the dialog is not open.
- [x] `defaultAction?: ReviewAction` — pre-select an action (e.g., "Resolve" when the admin clicked the "Resolve" menu item directly).

#### Internal state

- [x] `selectedAction: ReviewAction | null` — initialised from `defaultAction` when the dialog opens.

#### Hooks to call inside the component

- [x] `const { mutate: reviewReport, isPending } = useReviewReport()`.

#### Layout

- [x] MUI `Dialog open={open} onClose={onClose} maxWidth="sm" fullWidth`.
- [x] `DialogTitle`: "Review Report" with a `CloseIcon` button top-right.
- [x] `DialogContent`:
  - If `report !== null`, show a brief summary of the report: entity type chip + reason text + "Reported by @{reporterUsername}" + creation date.
  - MUI `FormControl` with `RadioGroup` for the action:
    - `RESOLVE` — label "Resolve: Take action and close this report"
    - `DISMISS` — label "Dismiss: No violation found, close this report"
    - `MARK_REVIEWED` — label "Mark as Reviewed: Acknowledged but monitoring"
- [x] `DialogActions`:
  - "Cancel" button calls `onClose`. Disabled while `isPending`.
  - "Confirm" button — disabled if `selectedAction === null` or `isPending`. Shows `CircularProgress size={16}` while `isPending`. On click: calls `reviewReport({ id: report.id, payload: { action: selectedAction } }, { onSuccess: onClose })`.

---

## Notes

- The admin pages are intentionally table-heavy. MUI's `Table` component is the right tool here — do not use a card grid layout for data with multiple columns.
- The `Chip` colour coding for status values creates instant visual scanning in a long table. Use `color` prop values that are available in the MUI `Chip` API: `"default" | "primary" | "secondary" | "error" | "info" | "success" | "warning"`.
- The suspension confirmation dialog is declared inline in `AdminUsersPage` rather than extracted to a separate file because it is only used in one place. Follow the guideline: extract only when used in two or more places.
- These admin pages do not need a back-navigation button — the admin panel has its own navigation within the `AdminRoute` wrapper which will include breadcrumbs or a sidebar (wire that in TASK-9.18).
