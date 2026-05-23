# TASK-9.15 — User-Facing Pages: BlockedAccountsPage

## Overview

Create `BlockedAccountsPage` — a settings page listing all users that the current user has blocked. Each entry shows the blocked user's avatar, username, display name, and the date the block was created, alongside an "Unblock" button. The page is accessible via `/settings/blocked`.

---

## Requirements

- Lives in `frontend/src/pages/settings/`.
- Must use `export default function` for `React.lazy` compatibility.
- MUI and `sx` prop only for all styling.
- Uses TanStack React Query for data fetching — no manual `useEffect` + `useState` for server state.

---

## File Location

```
frontend/src/pages/settings/BlockedAccountsPage.tsx
```

---

## Checklist

### Custom hook prerequisite

- [ ] Create `frontend/src/hooks/moderation/useBlockedUsers.ts` before building this page.
  - Uses `useQuery` with `queryKey: ['blocked-users']` and `queryFn: () => moderationApi.getBlockedUsers(0, 100)`.
  - Returns `{ blockedUsers, isLoading, isError }` where `blockedUsers` is `UserBlock[]` (defaulting to `[]`).
  - `staleTime`: `60_000` (1 minute) — the blocked list does not change frequently.
  - Invalidate this query key from `useUnblockUser.ts` (TASK-9.14) on successful unblock so the list refreshes immediately.

---

### Page structure

#### Layout root

- [ ] MUI `Box` with `maxWidth: 600`, `mx: 'auto'`, `py: 3`, `px: { xs: 2, sm: 3 }` — centred narrow column consistent with other settings pages.

#### Page header

- [ ] MUI `Typography variant="h5" fontWeight={600} mb={1}` — "Blocked Accounts".
- [ ] MUI `Typography variant="body2" color="text.secondary" mb={3}` — "People you've blocked can't see your posts or find your profile. They won't be notified when you unblock them."

#### Data fetching

- [ ] Call `const { blockedUsers, isLoading, isError } = useBlockedUsers()`.

#### Loading state

- [ ] While `isLoading` is `true`: render a list of 5 skeleton rows. Each skeleton row mimics the shape of a real item: circular `Skeleton` (avatar) + rectangular `Skeleton` (username + display name) side by side, with a rectangular `Skeleton` button on the right. Use MUI `Skeleton` component with `animation="wave"`.

#### Error state

- [ ] When `isError` is `true`: render an MUI `Alert severity="error"` component with the message "Could not load blocked accounts. Please try again."

#### Empty state

- [ ] When `!isLoading && !isError && blockedUsers.length === 0`: render a centred MUI `Box` with `py: 6` containing `Typography color="text.secondary" align="center"` — "You haven't blocked anyone yet."

#### Blocked users list

- [ ] When `blockedUsers.length > 0`: render MUI `List disablePadding` with a `Divider` between each item.
- [ ] Each item is a `ListItem` (not a `ListItemButton` — clicking the row does nothing, only the Unblock button is interactive):
  - `ListItemAvatar`: MUI `Avatar` with `src={user.avatarUrl ?? undefined}` and the first letter of `user.username` as fallback text.
  - `ListItemText`:
    - `primary`: MUI `Typography variant="body2" fontWeight={500}` — `user.username`.
    - `secondary`: two stacked lines:
      - `user.fullName` if not null, otherwise omit.
      - Relative timestamp: "Blocked {formatDistanceToNow(new Date(user.blockedAt), { addSuffix: true })}". Wrap in a `try/catch` in case `blockedAt` is malformed.
  - `ListItemSecondaryAction`: MUI `Button variant="outlined" size="small"` — "Unblock" — that calls `unblock(user.username)` on click. Disable the button while `isPending` for that specific username (track which username is being unblocked to avoid disabling all Unblock buttons simultaneously).

#### Tracking pending unblocks

- [ ] Because `useUnblockUser` is a single mutation shared across all rows, you need to know which row is "in flight". Declare `const [pendingUsername, setPendingUsername] = useState<string | null>(null)`. On the Unblock button's `onClick`: set `setPendingUsername(user.username)`, then call `unblock(user.username, { onSettled: () => setPendingUsername(null) })`. Disable the specific Unblock button where `pendingUsername === user.username`.

#### Navigation back link

- [ ] At the top of the page, above the header, add a MUI `Button variant="text" startIcon={<ArrowBackIcon />}` — "Back to Settings" — that calls `navigate(-1)` from `useNavigate()`. This allows mobile users to return to the settings menu without using the browser back button.

---

## Notes

- `BlockedAccountsPage` is a settings page, not a social page. Its layout should be consistent with other pages in `pages/settings/` (e.g., `NotificationSettingsPage` from Phase 7). Review that page before building this one to match the visual style.
- The page fetches all blocked users in a single call (`size = 100`) rather than paginating. Blocking hundreds of users is an unusual edge case. If the list grows beyond 100, add pagination in a follow-up task.
- `formatDistanceToNow` from `date-fns` was already added as a dependency in Phase 7 (TASK-7.18). Verify it is in `package.json` before importing.
- The Unblock button should NOT show a confirmation dialog — unblocking is immediately reversible, so the friction of a confirmation is unnecessary here. The confirmation is only appropriate for the initial block action (`BlockButton` in TASK-9.14).
