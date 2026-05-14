# TASK-7.17 — Custom Hooks: useNotifications, useNotificationSettings, useUnreadNotifications

## Overview

Create three hooks that form the data layer for notifications. Components call these hooks and never touch `notificationsApi` directly.

## Requirements

- Use `useInfiniteQuery`, `useQuery`, `useMutation` from TanStack React Query v5 — no manual `useEffect` + `useState` for fetching.
- Query keys follow project convention: `['notifications']`, `['notification-settings']`.

## File Locations

```
frontend/src/hooks/useNotifications.ts
frontend/src/hooks/useNotificationSettings.ts
frontend/src/hooks/useUnreadNotifications.ts
```

---

## Instructions

### `useNotifications.ts`

**Purpose:** Fetches a paginated notification list and exposes mutations to mark notifications as read.

**Fetching — use `useInfiniteQuery`:**
`useInfiniteQuery` is the React Query hook for "load more" lists. It works by calling your fetch function repeatedly with a changing `pageParam`. Each call returns one page worth of data, and React Query accumulates all pages internally.

- Set `queryKey` to `['notifications']`.
- Set `queryFn` to call `notificationsApi.getNotifications(pageParam)` where `pageParam` starts at `0`.
- Set `initialPageParam` to `0`.
- Set `getNextPageParam`: receive the last fetched page and all pages so far. If the last page contained 20 items (the page size limit), there might be more — return the total number of pages fetched so far as the next page number. If fewer than 20 items came back, return `undefined` to tell React Query there are no more pages.

**Flattening pages into a list:**
`useInfiniteQuery` stores data as an array of pages (each page is an array of notifications). Before returning from the hook, flatten this into a single array using `.flat()` so the component receives a simple `Notification[]`.

**Mutations:**
- `markRead`: calls `notificationsApi.markRead(id)`, then on success invalidates the `['notifications']` query so the list refreshes.
- `markAllRead`: calls `notificationsApi.markAllRead()`, then on success invalidates `['notifications']`.

**Return from the hook:**
- `notifications` — the flattened array (default to empty array if data is not yet loaded)
- `isLoading`
- `isFetchingNextPage` — true while the next page is being fetched (used to show a bottom spinner)
- `fetchNextPage` — function to trigger loading the next page (called by the scroll sentinel)
- `hasNextPage` — boolean; false when `getNextPageParam` returned `undefined`
- `markRead(id)` — calls the mutation
- `markAllRead()` — calls the mutation

---

### `useNotificationSettings.ts`

**Purpose:** Fetches the user's notification preferences and provides a mutation to update them.

**Fetching — use `useQuery`:**
- `queryKey`: `['notification-settings']`
- `queryFn`: calls `notificationsApi.getSettings()`

**Mutation for updating:**
- `mutationFn`: calls `notificationsApi.updateSettings(settings)` with the full settings object as the body.
- On success: invalidate `['notification-settings']` so the query refetches fresh data from the server.

**Return from the hook:**
- `settings` — the `NotificationSettings` object, or `undefined` while loading
- `isLoading`
- `updateSettings(settings)` — calls the mutation
- `isUpdating` — `true` while the mutation request is in-flight (use this to disable the toggle switches)

---

### `useUnreadNotifications.ts`

**Purpose:** Maintains a live unread count that increments in real time as WebSocket notifications arrive, without waiting for a full list refetch.

**Check first:** Look at `useWebSocket.ts` from TASK-6.16. If it already provides a way to subscribe to additional user-specific topics, add notification subscription there instead of creating a second STOMP connection.

**If a separate connection is needed:**

Use `useRef` to hold the STOMP client instance. Here is why this matters: if you declared the client as a plain variable inside the component function, React would create a brand new client object on every re-render (which can happen many times per second). `useRef` gives you a container whose value persists for the entire lifetime of the component without triggering re-renders when it changes. Assign the client to `clientRef.current` inside a `useEffect`.

Set up the STOMP connection inside a `useEffect` with an **empty dependency array** `[]`. An empty dependency array means the effect runs exactly once — when the component mounts — and the cleanup function runs when the component unmounts. Inside the effect:
1. Read the JWT token the same way `useWebSocket.ts` does (check that file and copy the token retrieval exactly).
2. Create a STOMP `Client` using `SockJS` as the WebSocket factory, pointing at the backend WebSocket endpoint.
3. Include the JWT in the STOMP `connectHeaders`.
4. In the `onConnect` callback, subscribe to `/user/topic/notifications`. When a frame arrives, increment the local unread count state and call `queryClient.invalidateQueries` for `['notifications']` so the full list refetches in the background.
5. Activate the client.
6. Return a cleanup function that deactivates the client when the component unmounts.

**Why `/user/topic/notifications` (no user ID in the path):**
The backend sends notifications using `convertAndSendToUser(userId, "/topic/notifications", ...)`. Spring's user-destination prefix (`/user`) translates this server-side to the correct STOMP session without the client needing to know its own ID. The client simply subscribes to `/user/topic/notifications` and Spring handles the routing automatically.

**Local state:**
Keep a `useState` counter for `unreadCount`, starting at `0`. Increment it each time a WebSocket frame arrives on the notifications topic.

**Return from the hook:**
- `unreadCount` — the current number of unread notifications since the page loaded
- `resetCount()` — sets the count back to `0`; call this in the UI after the user clicks "Mark all as read"

## Notes

- `queryClient` is obtained by calling `useQueryClient()` inside the hook.
- Import `Client` from `'@stomp/stompjs'` and `SockJS` from `'sockjs-client'` — check that these are already in `package.json` (they were installed for messaging in phase 6).
