# TASK-6.14 — TypeScript Types: messaging.ts

## Overview

Create a single `messaging.ts` types file that models conversations, members, and messages. These types mirror the backend DTOs (`ConversationResponse`, `MessageResponse`) exactly.

## Requirements

- No `any` types. Strict TypeScript.
- Enums for `MessageType` and `MessageStatus` — use string literal union types (not TypeScript `enum`) to keep them serialisation-friendly.
- `Message.status` only exists for messages sent by the current user — mark it optional or nullable.

## File Location

```
frontend/src/types/messaging.ts
```

---

## Checklist

### Types to define

- [ ] `MessageType` — string union: `'TEXT' | 'IMAGE' | 'VIDEO' | 'POST_SHARE'`
- [ ] `MessageStatus` — string union: `'SENT' | 'DELIVERED' | 'READ'`
- [ ] `ConversationMember` interface:
  - `userId: string`
  - `username: string`
  - `avatarUrl: string | null`
  - `role: 'OWNER' | 'MEMBER'`
- [ ] `Message` interface:
  - `id: string`
  - `conversationId: string`
  - `senderId: string`
  - `senderUsername: string`
  - `senderAvatarUrl: string | null`
  - `content: string | null`
  - `messageType: MessageType`
  - `mediaUrl: string | null`
  - `sharedPostId: string | null`
  - `status: MessageStatus | null`
  - `createdAt: string` (ISO 8601)
- [ ] `Conversation` interface:
  - `id: string`
  - `name: string | null`
  - `isGroup: boolean`
  - `lastMessage: Message | null`
  - `unreadCount: number`
  - `createdAt: string`
- [ ] `SendMessagePayload` interface (request body):
  - `content: string | null`
  - `messageType: MessageType`
  - `mediaUrl?: string`
  - `sharedPostId?: string`
- [ ] `CreateConversationPayload` interface:
  - `participantIds: string[]`
  - `name?: string`
  - `isGroup: boolean`

## Notes

- Use `string` for all UUID fields (not `UUID` type) — Axios deserializes them as strings.
- `MessageType` and `MessageStatus` as string unions (not `enum`) means they are directly comparable to values from the API without conversion.
- `Conversation.name` is `null` for 1-to-1 chats — the UI should derive the display name from the other participant's username in that case.
