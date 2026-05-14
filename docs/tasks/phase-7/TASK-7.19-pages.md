# TASK-7.19 — Pages: NotificationsPage, NotificationSettingsPage

## Overview

Create two route-level pages. `NotificationsPage` shows the full notification list grouped by time. `NotificationSettingsPage` shows toggle switches for each notification preference.

## Requirements

- Pages live in `frontend/src/pages/notifications/`.
- Both are protected routes (the router handles the redirect — TASK-7.21).
- Both must use `export default` — required for React.lazy compatibility.
- MUI and `sx` only for all styling.

## File Locations

```
frontend/src/pages/notifications/NotificationsPage.tsx
frontend/src/pages/notifications/NotificationSettingsPage.tsx
```

---

## Instructions

### `NotificationsPage.tsx`

**Purpose:** Full paginated list of all notifications, grouped into time buckets, with infinite scroll.

**Data:** Call `useNotifications()` and destructure `notifications`, `isLoading`, `isFetchingNextPage`, `fetchNextPage`, `hasNextPage`, `markRead`, and `markAllRead`.

**Time grouping:**
After getting the `notifications` array, split it into three filtered sub-arrays before rendering:
- **Today**: notifications whose `createdAt` is less than 24 hours ago.
- **This Week**: notifications between 24 hours and 7 days ago.
- **Earlier**: everything older than 7 days.
Compute the age of each notification by comparing `new Date(notification.createdAt)` against `new Date()`.

**Page layout:**
- Root: MUI `Box` with `maxWidth: 600`, `mx: 'auto'`, `py: 2`, and responsive horizontal padding (`px: { xs: 1, sm: 2 }`).
- Title row: a `Box` with `display: 'flex'`, `justifyContent: 'space-between'`, `alignItems: 'center'`, `mb: 2`. Left side: `Typography variant="h5" fontWeight={600}` — "Notifications". Right side: MUI `Button variant="outlined" size="small"` — "Mark all as read", which calls `markAllRead()`.

**Loading state:** While `isLoading` is true, render 5 `Skeleton` items with `variant="rectangular"`, `height={72}`, `borderRadius: 1`, and a bottom margin between each.

**Empty state:** When not loading and `notifications.length === 0`, show a centred `Box` with `py: 8` and a `Typography color="text.secondary"` — "You're all caught up!".

**Grouped list:** Use MUI `List`. For each non-empty time bucket, render:
1. MUI `ListSubheader` with `bgcolor: 'background.paper'` and `fontWeight: 600` — labels "Today", "This Week", "Earlier".
2. One `NotificationItem` per notification in the bucket, passing `onRead={markRead}`.
3. MUI `Divider` between buckets (skip the divider after the last non-empty bucket).

**Infinite scroll — `IntersectionObserver`:**
Place an invisible `div` (with a `ref`) immediately after the last notification item. Use `IntersectionObserver` inside a `useEffect` to watch this sentinel element. When the sentinel scrolls into the viewport, call `fetchNextPage()` — but only if `hasNextPage` is `true` and `isFetchingNextPage` is `false` (to prevent duplicate requests).

How `IntersectionObserver` works: you create an observer, tell it which element to watch, and provide a callback. The callback receives an array of `IntersectionObserverEntry` objects — check the first entry's `isIntersecting` property. If it is `true`, the element is visible in the viewport.

The `useEffect` must list `hasNextPage`, `isFetchingNextPage`, and `fetchNextPage` in its dependency array so the observer callback always has fresh values. Return a cleanup function from the effect that calls `observer.disconnect()` to stop watching when the component unmounts.

Below the sentinel, show a centred `CircularProgress size={24}` inside a `Box` while `isFetchingNextPage` is true.

---

### `NotificationSettingsPage.tsx`

**Purpose:** A list of labelled toggle switches for the user's notification preferences.

**Data:** Call `useNotificationSettings()` and destructure `settings`, `isLoading`, `updateSettings`, and `isUpdating`.

**Local optimistic state:**
Keep a local copy of the settings in `useState` initialised to `null`. Use a `useEffect` that watches the `settings` value from the query: when it becomes available (changes from `undefined` to a real object), copy it into the local state. This syncs the local copy once on load and again if the server data changes.

When the user toggles a switch, update the local state immediately (so the UI responds instantly) AND call `updateSettings` with the new values. Without the local copy, the toggle would snap back to its old position while the API call is in-flight.

**Why this pattern exists:** React Query's `settings` value is read-only — it reflects what the server last returned. To give the user instant feedback, you keep a writable local shadow copy. The local copy is the source of truth for the `checked` prop of each `Switch`. The server copy (from the query) re-syncs the local copy whenever the query refetches.

**Toggle handler:**
Write a single `handleToggle` function that accepts the name of the settings field to flip. Inside it, spread the current local settings into a new object, negate the targeted field, set that as the new local state, and call `updateSettings` with it. Use `keyof NotificationSettings` as the type of the field parameter — this is a TypeScript type that equals the union of all valid field names, so TypeScript will error at compile time if you pass a wrong name.

**Layout:**
- Root: MUI `Box` with `maxWidth: 500`, `mx: 'auto'`, `py: 2`, responsive `px`.
- Title: `Typography variant="h5" fontWeight={600} mb={3}` — "Notification Settings".
- Loading state: 5 `Skeleton` items with `height={56}`.
- Settings list: MUI `List disablePadding`. Define the five rows as a data array (array of objects with `field` and `label`) and map over it rather than writing five separate `ListItem` blocks. Each row:
  - MUI `ListItem`.
  - `ListItemText primary={label}`.
  - MUI `Switch` with `checked={local[field]}`, `onChange={() => handleToggle(field)}`, `disabled={isUpdating}`.

**Why define rows as data:** Mapping over an array of `{ field, label }` objects is idiomatic React — the UI is driven by data. It also means adding a new toggle row later only requires adding one entry to the array, not duplicating a block of JSX.

## Notes

- Both pages use `export default function ...` — confirm this before registering the routes in TASK-7.21.
- `isFetchingNextPage` must be included in the return value of `useNotifications` — add it if not already there.
- `ListSubheader` is imported from `@mui/material`. It renders as a sticky section header inside a MUI `List`.
- `CircularProgress` and `Skeleton` are both from `@mui/material`.
