# TASK-6.20 — Unread Badge in Navigation

## Overview

Update the navigation/app bar to display a live unread message count badge on the inbox icon. The count updates in real time via WebSocket and falls back to the conversation list data.

## Requirements

- Modify the existing navigation component — do **not** create a new one.
- First find the navigation component: check `frontend/src/AppShell.tsx` or any nav-bar component that contains the messaging icon.

## File to Modify

```
frontend/src/AppShell.tsx   ← or wherever the nav icons live
```

---

## Instructions

### Locating the nav icon

- Search for the existing inbox/messages icon in the app shell (likely a `MailOutlineIcon` or `ChatBubbleOutlineIcon` from `@mui/icons-material`).
- If no messaging icon exists yet, add one next to the existing nav icons.

### Adding the badge

- Wrap the `IconButton` with a MUI `Badge`:
  ```
  <Badge badgeContent={unreadCount} color="error" max={99}>
    <IconButton ...>
      <MailOutlineIcon />
    </IconButton>
  </Badge>
  ```
- `color="error"` matches Instagram's red notification badge style.
- `max={99}` shows "99+" when count exceeds 99 (MUI default behaviour).

### Getting the unread count

**Primary source — WebSocket (real-time):**
- Call `useWebSocket(null)` in the app shell (passing `null` since no specific conversation is open).
- The hook exposes a `totalUnreadCount: number` derived from the `/user/{userId}/topic/unread-count` subscription (see TASK-6.16).
- Store this in the hook as local state, updated on each incoming event.

**Fallback — derived from conversation list:**
- If the WebSocket is disconnected (`isConnected === false`) or the unread count hasn't been received yet, derive the count from `useConversations`:
  ```
  const unreadCount = conversations.reduce((sum, c) => sum + c.unreadCount, 0)
  ```
- Use `useConversations` regardless; it keeps the badge accurate after a page refresh even before the WebSocket reconnects.

**Combining both:**
- Prefer the WebSocket-pushed `totalUnreadCount` when available (`isConnected && totalUnreadCount !== null`).
- Fall back to the derived sum from conversation data otherwise.

### Badge visibility

- Hide the badge (or show `0` without rendering the badge) when `unreadCount === 0`:
  - MUI `Badge` hides itself automatically when `badgeContent={0}` and `showZero` is not set.

## Notes

- Do not add WebSocket logic directly inside the nav component — consume it through the `useWebSocket` hook.
- `useConversations` is already called elsewhere in `InboxPage`; React Query deduplicates the request via the shared cache, so calling it in the nav component incurs no extra network cost.
- The badge should update without a page refresh when the user receives a new message while on a different page.
