# TASK-6.16 — WebSocket Hook: useWebSocket.ts

## Overview

Create a custom hook that manages a single persistent STOMP/SockJS connection, handles subscription lifecycle, and integrates with React Query's cache on incoming messages.

## Requirements

- Uses `@stomp/stompjs` and `sockjs-client` — install if not already present.
- Single shared connection per user session (not per component).
- JWT is sent in the STOMP `CONNECT` headers.
- On incoming message: update the React Query cache rather than using local state.
- Reconnects automatically with exponential backoff on disconnect.

## File Location

```
frontend/src/hooks/useWebSocket.ts
```

---

## Instructions

### Connection setup

- Use `new Client({ brokerURL, webSocketFactory, connectHeaders })` from `@stomp/stompjs`.
- `brokerURL` should be the WebSocket URL: `ws://localhost:8080/ws`.
- For SockJS fallback: set `webSocketFactory` to `() => new SockJS('/ws')` (proxied by Vite dev server, or use absolute URL).
- `connectHeaders` must include `{ Authorization: 'Bearer <jwt>' }`. Read the JWT from the same auth storage used elsewhere in the app (check how `client.ts` attaches the token — use the same source).
- Set `reconnectDelay` to a sensible default (e.g., 5000ms). The library handles exponential backoff internally when `reconnectDelay` is set.

### Hook interface

The hook should accept:
- `conversationId: string | null` — the currently open conversation (or `null` if inbox is open)

The hook should expose:
- `sendMessage(payload: SendMessagePayload): void` — publishes to `/app/chat.send` with `conversationId` included
- `sendTyping(conversationId: string, isTyping: boolean): void` — publishes to `/app/chat.typing`
- `isConnected: boolean`

### Subscription management

- Subscribe to `/topic/conversations/{conversationId}` when `conversationId` is non-null.
- Unsubscribe and re-subscribe when `conversationId` changes (use `useEffect` cleanup).
- Subscribe to `/user/{userId}/topic/unread-count` globally to receive unread count updates (for the navigation badge in TASK-6.20).

### Cache integration

On receiving a new message from the subscription:
- Parse the payload as `Message`.
- Update the React Query cache for `['messages', conversationId]` by prepending the new message (messages list is newest-first).
- Update the React Query cache for `['conversations']` by updating `lastMessage` on the matching conversation and incrementing `unreadCount` (unless the conversation is currently open).

### Typing indicators

- Incoming typing events from `/topic/conversations/{conversationId}/typing` should be stored in local `useState` inside the hook and exposed as `typingUserIds: string[]`.
- Auto-clear a user from `typingUserIds` after 3 seconds of no update (use `setTimeout` + cleanup).

## Notes

- The STOMP `Client` instance should be created once using `useRef` so it persists across re-renders without triggering reconnects.
- Activate the client in a `useEffect` with an empty dependency array; deactivate in the cleanup function.
- Do not call `client.publish` before the client is connected — guard with `client.connected`.
- MUI components are not involved in this hook — it is pure logic.
