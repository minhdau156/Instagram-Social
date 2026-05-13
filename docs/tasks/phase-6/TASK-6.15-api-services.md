# TASK-6.15 — API Service: messagingApi.ts

## Overview

Create `messagingApi.ts` — the Axios-based API service for all messaging HTTP calls. Follows the same pattern as `feedApi.ts` and `postsApi.ts`.

## Requirements

- Use the shared Axios instance from `src/api/client.ts` — no direct `fetch` calls.
- All functions are `async` and return typed data.
- No business logic — pure HTTP calls.

## File Location

```
frontend/src/api/messagingApi.ts
```

---

## Checklist

### Functions to implement

- [x] `getConversations(page?: number, size?: number): Promise<Conversation[]>`
  - `GET /api/v1/conversations?page={page}&size={size}`
- [x] `createConversation(payload: CreateConversationPayload): Promise<Conversation>`
  - `POST /api/v1/conversations`
- [x] `getMessages(conversationId: string, cursor?: string, limit?: number): Promise<Message[]>`
  - `GET /api/v1/conversations/{conversationId}/messages?cursor={cursor}&limit={limit}`
  - Omit `cursor` param when it is `undefined`.
- [x] `sendMessage(conversationId: string, payload: SendMessagePayload): Promise<Message>`
  - `POST /api/v1/conversations/{conversationId}/messages`
- [x] `markRead(conversationId: string): Promise<void>`
  - `PUT /api/v1/conversations/{conversationId}/read`
- [x] `addGroupMembers(conversationId: string, memberIds: string[]): Promise<void>`
  - `POST /api/v1/conversations/{conversationId}/members` with body `{ memberIds }`
- [x] `leaveConversation(conversationId: string): Promise<void>`
  - `DELETE /api/v1/conversations/{conversationId}/members/me`

### Export style

- [x] Export as a named object `messagingApi` with all functions as methods (same style as `postsApi`).

## Notes

- Import types from `../types/messaging`.
- Use `r.data` to unwrap Axios responses: `api.get<Conversation[]>(...).then(r => r.data)`.
- For `getMessages`, build the query params object conditionally — only include `cursor` if it is defined, to avoid sending `cursor=undefined` to the server.
- Default `page = 0`, `size = 20`, `limit = 30`.
