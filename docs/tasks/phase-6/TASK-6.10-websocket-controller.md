# TASK-6.10 — WebSocket Controller

## Overview

Create the STOMP message controller that handles real-time messaging events. It receives send and typing events from clients, processes them via use-case interfaces, and broadcasts responses to conversation topics.

## Requirements

- Lives in `adapter/in/messaging/` (a new sub-package alongside `adapter/in/web/`).
- `@Controller` (not `@RestController`) — STOMP controllers use `@MessageMapping`.
- Delegates to use-case interfaces — no business logic here.
- Broadcasts via `SimpMessagingTemplate` to conversation topics.

## File Location

```
backend/src/main/java/com/instagram/adapter/in/messaging/MessageWebSocketController.java
```

---

## Checklist

### Class setup

- [x] `@Controller`
- [x] Constructor injects: `SendMessageUseCase`, `SimpMessagingTemplate`

### `@MessageMapping("/chat.send")`

- [x] Accept a `SendMessageRequest` payload (the same DTO from TASK-6.12 or a dedicated WebSocket payload record).
- [x] Extract sender ID from `Principal principal` parameter (the authenticated user set by the channel interceptor).
- [x] Build a `SendMessageUseCase.Command` and call `sendMessageUseCase.sendMessage(command)`.
- [x] The service already broadcasts to `/topic/conversations/{conversationId}` — do **not** duplicate the broadcast here.
- [x] Return `void` or the `MessageResponse` (if you want to send an ACK back to the sender via `@SendToUser`).

### `@MessageMapping("/chat.typing")`

- [x] Accept a simple `TypingPayload` record: `{ UUID conversationId, boolean isTyping }`.
- [x] Extract sender ID from `Principal`.
- [x] Build a `TypingEvent` record: `{ UUID conversationId, UUID userId, boolean isTyping }`.
- [x] Broadcast to `/topic/conversations/{conversationId}/typing` using `simpMessagingTemplate.convertAndSend(...)`.
- [x] No use-case needed — typing indicators are ephemeral and not persisted.

## Notes

- `Principal.getName()` returns the user's UUID string (set during authentication in TASK-6.9). Parse it with `UUID.fromString(principal.getName())`.
- The `@MessageMapping` path is relative to the `/app` prefix configured in TASK-6.9, so the full client destination is `/app/chat.send`.
- Clients subscribe to `/topic/conversations/{id}` for new messages and `/topic/conversations/{id}/typing` for typing indicators.
- If a `NotConversationMemberException` is thrown during `sendMessage`, catch it and use `simpMessagingTemplate.convertAndSendToUser` to send an error frame back to the sender only — do not broadcast errors to the whole conversation.
