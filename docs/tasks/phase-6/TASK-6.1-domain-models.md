# TASK-6.1 — Domain Models: Conversation, ConversationMember, Message

## Overview

Create the three core domain models for the Direct Messaging feature. All models follow the same hand-written Builder pattern used in `Post.java` — no Lombok, no framework annotations.

## Requirements

- Lives in `domain/model/` — **no** Spring, JPA, or Lombok imports.
- Use the existing `Post.java` as the reference for Builder pattern and immutable-copy behaviour.
- `MessageType` and `MessageStatus` are enums defined **inside** `Message.java` as public static enums.
- `ConversationMemberRole` is a public static enum inside `ConversationMember.java`.

## File Locations

```
backend/src/main/java/com/instagram/domain/model/Conversation.java
backend/src/main/java/com/instagram/domain/model/ConversationMember.java
backend/src/main/java/com/instagram/domain/model/Message.java
```

---

## Checklist

### `Conversation.java`

- [ ] Fields:
  - `UUID id`
  - `String name` (nullable — only used for group chats)
  - `boolean isGroup`
  - `OffsetDateTime createdAt`
  - `OffsetDateTime updatedAt`
- [ ] Builder with all fields.
- [ ] Business method `withUpdatedName(String name)` — returns a new `Conversation` with the updated name and `updatedAt = OffsetDateTime.now()`.
- [ ] No setters.

### `ConversationMember.java`

- [ ] Public static enum `Role { OWNER, MEMBER }`.
- [ ] Fields:
  - `UUID conversationId`
  - `UUID userId`
  - `Role role`
  - `OffsetDateTime joinedAt`
- [ ] Builder with all fields.
- [ ] No business behaviour methods needed.

### `Message.java`

- [ ] Public static enum `MessageType { TEXT, IMAGE, VIDEO, POST_SHARE }`.
- [ ] Public static enum `MessageStatus { SENT, DELIVERED, READ }`.
- [ ] Fields:
  - `UUID id`
  - `UUID conversationId`
  - `UUID senderId`
  - `String content` (nullable for IMAGE/VIDEO messages)
  - `MessageType messageType`
  - `String mediaUrl` (nullable)
  - `UUID sharedPostId` (nullable — only for `POST_SHARE` type)
  - `MessageStatus status`
  - `OffsetDateTime createdAt`
- [ ] Builder with all fields.
- [ ] Business method `withRead()` — returns a new `Message` with `status = MessageStatus.READ`.

## Notes

- All three models are immutable via copy — no field mutation after construction.
- `Conversation.updatedAt` should be bumped whenever a new message is sent in that conversation (the service layer handles this — the domain just exposes `withUpdatedName`).
- Do **not** embed a `List<ConversationMember>` inside `Conversation` — keep models flat; the service assembles them from separate repository calls.
