# TASK-6.17 — Custom Hooks: useConversations, useMessages, useSendMessage

## Overview

Create three React Query hooks that manage the data layer for conversations and messages. These hooks wrap `messagingApi` and handle cache state, pagination, and optimistic updates.

## Requirements

- All server state managed by React Query (no manual `useEffect` + `useState` for fetching).
- Follow the same patterns as `useHomeFeed` and `usePosts`.

## File Locations

```
frontend/src/hooks/useConversations.ts
frontend/src/hooks/useMessages.ts
frontend/src/hooks/useSendMessage.ts
```

---

## Instructions

### `useConversations.ts`

- Use `useQuery` with query key `['conversations']`.
- Query function: `messagingApi.getConversations()`.
- `staleTime`: 30 seconds — inbox is kept fresh by WebSocket updates (TASK-6.16).
- Expose: `{ conversations, isLoading, isError }`.
- The hook does **not** manage WebSocket subscription itself — that is handled by `useWebSocket`. The cache update from WebSocket events (TASK-6.16) will automatically trigger a re-render here.

### `useMessages.ts`

- Use `useInfiniteQuery` with query key `['messages', conversationId]`.
- Query function: `messagingApi.getMessages(conversationId, pageParam)` where `pageParam` is the cursor UUID.
- `getNextPageParam`: look at the last loaded page — if it returned fewer items than `limit`, return `undefined` (no more pages); otherwise return the `id` of the oldest message in that page.
- Pages load **upward** (older messages) — the initial fetch gives the latest messages, and `fetchNextPage` loads older ones.
- Expose: `{ messages, isLoading, isError, fetchNextPage, hasNextPage, isFetchingNextPage }`.
- Flatten pages: `data?.pages.flatMap(page => page) ?? []` — the API returns `Message[]` per page, not a wrapper object.
- `enabled`: only run when `conversationId` is a non-empty string.

### `useSendMessage.ts`

- Use `useMutation` calling `messagingApi.sendMessage(conversationId, payload)`.
- **Optimistic update** on `onMutate`:
  - Snapshot current `['messages', conversationId]` cache.
  - Prepend an optimistic `Message` to the cache with a temporary `id` (e.g., `'optimistic-' + Date.now()`), `status: 'SENT'`, and the payload fields filled in.
  - Return the snapshot for rollback.
- On `onError`: roll back to snapshot using `queryClient.setQueryData`.
- On `onSuccess`: replace the optimistic entry with the real message from the server response (match by replacing the entry whose `id` starts with `'optimistic-'`).
- Expose: `{ sendMessage, isPending }`.

## Notes

- `useMessages` returns messages in newest-first order (as returned by the API). The `ChatPage` component will reverse this for display (oldest at top, newest at bottom).
- After `markRead` is called (from `ChatPage` on mount), invalidate `['conversations']` to update `unreadCount` in the sidebar.
- The optimistic message in `useSendMessage` needs `senderId` — get it from `useAuth().user.id`.
