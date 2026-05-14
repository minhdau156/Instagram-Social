# TASK-7.18 — Notification Components: NotificationItem, NotificationDropdown, UnreadBadge

## Overview

Create three reusable notification UI components. `UnreadBadge` is the bell icon button. `NotificationDropdown` is the popover panel. `NotificationItem` is a single row used inside both the dropdown and the full page.

## Requirements

- Lives in `frontend/src/components/notifications/`.
- MUI components and `sx` prop only — no inline `style={{}}`, no hardcoded hex colours.
- Fully typed — no `any`.
- Named exports (not default exports) — these are shared components, not pages.

## File Locations

```
frontend/src/components/notifications/UnreadBadge.tsx
frontend/src/components/notifications/NotificationItem.tsx
frontend/src/components/notifications/NotificationDropdown.tsx
```

---

## Instructions

### `UnreadBadge.tsx`

**Purpose:** Bell icon button with a red count badge. Clicking it triggers the dropdown.

**Props:**
- `onClick`: a function that receives a `React.MouseEvent<HTMLButtonElement>` — the parent uses the event to read `e.currentTarget` as the popover anchor.
- `unreadCount: number`.

**Layout:**
- Root: MUI `IconButton` with `color="inherit"` so the icon matches the app bar's text colour.
- Inside the button: wrap `NotificationsOutlinedIcon` (from `@mui/icons-material`) inside an MUI `Badge`.
  - Set `badgeContent` to `unreadCount`.
  - Set `color` to `"error"` for the standard red notification badge.
  - Set `invisible` to `true` when `unreadCount` is `0` — this hides the badge entirely rather than showing a `0`.

---

### `NotificationItem.tsx`

**Purpose:** A single notification row. Used in both the dropdown (top 10) and the full page (all).

**Props:**
- `notification: Notification`
- `onRead: (id: string) => void` — called when the item is clicked; the parent decides what mutation to fire.

**Action text helper:**
Define a helper function inside the file (above the component) that takes a `Notification` and returns a human-readable sentence. Use a `switch` on `notification.type`:
- `LIKE` → `"{actorUsername} liked your photo"`
- `COMMENT` → `"{actorUsername} commented on your photo"`
- `MENTION` → `"{actorUsername} mentioned you in a comment"`
- `FOLLOW` → `"{actorUsername} started following you"`
- `FOLLOW_REQUEST` → `"{actorUsername} requested to follow you"`
- `MESSAGE` → `"{actorUsername} sent you a message"`
- Default → `"You have a new notification"`

If `actorUsername` is `null` (system notification), fall back to `"Someone"`.

**Navigation helper:**
Define a second helper function that takes a `Notification` and returns the path to navigate to on click. Use `entityType`:
- `POST` → `/posts/{entityId}` (fall back to `/` if `entityId` is null)
- `FOLLOW` or `FOLLOW_REQUEST` → `/profile/{actorUsername}` (fall back to `/` if null)
- `MESSAGE` → `/messages`
- Default → `/`

**Layout:**
- Root: MUI `ListItemButton`.
  - `onClick`: call `onRead(notification.id)`, then call `navigate(...)` using `useNavigate` from `react-router-dom`.
  - `sx`: set `bgcolor` to `'action.hover'` when `!notification.isRead`, otherwise `'transparent'`. The `action.hover` token is the MUI theme's subtle hover colour — it gives unread items a light background highlight without hardcoding a hex value.
- `ListItemAvatar`: MUI `Avatar` with `src` set to `actorAvatarUrl` (or `undefined` if null). Place the first uppercase character of `actorUsername` as the child text of `Avatar` — MUI shows this as a fallback letter when `src` fails to load or is missing.
- `ListItemText`:
  - `primary`: the action sentence from the helper function.
  - `secondary`: relative timestamp. Import `formatDistanceToNow` from `date-fns` and call it with `new Date(notification.createdAt)` and the option `{ addSuffix: true }` to get strings like "3 minutes ago".
  - `primaryTypographyProps`: set `fontWeight` to `600` for unread notifications and `400` for read ones.
- Optional unread dot: a small `Box` with `width: 8`, `height: 8`, `borderRadius: '50%'`, `bgcolor: 'primary.main'` placed after `ListItemText`. Render it only when `!notification.isRead`.

**Check `date-fns`:** Before importing `formatDistanceToNow`, verify `date-fns` is in `package.json`. If absent, run `npm install date-fns`.

---

### `NotificationDropdown.tsx`

**Purpose:** A MUI `Popover` panel showing the 10 most recent notifications with "Mark all as read" and "See all" actions.

**Props:**
- `anchorEl: HTMLButtonElement | null` — the bell button DOM element. When `null` the popover is closed; when it holds the element the popover is open.
- `onClose: () => void` — called when the user clicks outside the popover.

**How `anchorEl` controls visibility:**
Pass `open={Boolean(anchorEl)}` to the `Popover`. `Boolean(null)` is `false` (closed); `Boolean(buttonElement)` is `true` (open). You never manage a separate `open` boolean — the presence of `anchorEl` itself is the open signal.

**Popover positioning:**
- `anchorOrigin`: `{ vertical: 'bottom', horizontal: 'right' }` — the popover originates from the bottom-right corner of the bell button.
- `transformOrigin`: `{ vertical: 'top', horizontal: 'right' }` — the top-right corner of the dropdown aligns to the anchor point.
- `PaperProps sx`: set `width: 360` and `maxHeight: 480` to give the dropdown a fixed size.
- Add a small `mt: 1` margin so the dropdown sits slightly below the button.

**Inside the Popover — three sections:**

1. **Header row** (`Box` with `display: 'flex'`, `justifyContent: 'space-between'`, `p: 2`):
   - Left: `Typography variant="h6"` — "Notifications".
   - Right: MUI `Button variant="text" size="small"` — "Mark all as read". Clicking it calls `markAllRead()` from `useNotifications`.
   - Below the header: MUI `Divider`.

2. **Scrollable list** (MUI `List` with `overflow: 'auto'` and a capped `maxHeight`):
   - **Loading state**: render 3 `Skeleton` items (`variant="rectangular"`, `height={60}`, small margin and border-radius) while `isLoading` is true.
   - **Empty state**: a centred `Typography color="text.secondary"` — "No notifications yet" — when not loading and the list is empty.
   - **Normal state**: render `notifications.slice(0, 10)` as `NotificationItem` components. Pass `onRead` as `(id) => { markRead(id); onClose(); }` so clicking a notification marks it and closes the dropdown.

3. **Footer** (after a `Divider`):
   - MUI `Button fullWidth variant="text"` — "See all notifications". On click, navigate to `/notifications` using `useNavigate` and call `onClose()`.

**Data:** Call `useNotifications()` inside this component to get `notifications`, `isLoading`, `markRead`, and `markAllRead`. Call `useNavigate()` for the "See all" link.

## Notes

- `NotificationItem` must not call any API or mutation directly — it receives `onRead` as a prop and the parent (`NotificationDropdown` or `NotificationsPage`) decides what to do.
- `getNotificationText` and the navigation helper are private to `NotificationItem.tsx` — do not export them.
