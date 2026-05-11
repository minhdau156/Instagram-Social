# TASK-6.4 — In-Ports: Use-Case Interfaces

## Overview

Create seven use-case interfaces (driving ports) for the Direct Messaging feature. One interface per use case, one method per interface, with an inner `Command` or `Query` record for input data.

## Requirements

- Lives in `domain/port/in/` — **no** Spring or JPA imports.
- Follow the pattern from `CreatePostUseCase.java`: inner `Command` / `Query` record, single method.
- Return domain types only.

## File Locations

```
backend/src/main/java/com/instagram/domain/port/in/messaging/CreateConversationUseCase.java
backend/src/main/java/com/instagram/domain/port/in/messaging/GetConversationsUseCase.java
backend/src/main/java/com/instagram/domain/port/in/messaging/GetMessagesUseCase.java
backend/src/main/java/com/instagram/domain/port/in/messaging/SendMessageUseCase.java
backend/src/main/java/com/instagram/domain/port/in/messaging/MarkReadUseCase.java
backend/src/main/java/com/instagram/domain/port/in/messaging/AddGroupMemberUseCase.java
backend/src/main/java/com/instagram/domain/port/in/messaging/LeaveConversationUseCase.java
```

> Place all seven files in the `messaging/` sub-package to avoid cluttering the top-level `port/in/` package.

---

## Checklist

### `CreateConversationUseCase.java`

- [x] Method: `Conversation createConversation(Command command)`
- [x] Inner record `Command`:
  - `UUID creatorId`
  - `List<UUID> participantIds`
  - `String name` (nullable — only for group chats)
  - `boolean isGroup`

### `GetConversationsUseCase.java`

- [x] Method: `List<Conversation> getConversations(Query query)`
- [x] Inner record `Query`:
  - `UUID userId`
  - `int page`
  - `int size`

### `GetMessagesUseCase.java`

- [x] Method: `List<Message> getMessages(Query query)`
- [x] Inner record `Query`:
  - `UUID conversationId`
  - `UUID requesterId`
  - `UUID cursor` (nullable — null for first/latest page)
  - `int limit`

### `SendMessageUseCase.java`

- [x] Method: `Message sendMessage(Command command)`
- [x] Inner record `Command`:
  - `UUID conversationId`
  - `UUID senderId`
  - `String content` (nullable for IMAGE/VIDEO)
  - `Message.MessageType messageType`
  - `String mediaUrl` (nullable)
  - `UUID sharedPostId` (nullable)

### `MarkReadUseCase.java`

- [x] Method: `void markRead(Command command)`
- [x] Inner record `Command`:
  - `UUID conversationId`
  - `UUID userId`

### `AddGroupMemberUseCase.java`

- [x] Method: `void addGroupMember(Command command)`
- [x] Inner record `Command`:
  - `UUID conversationId`
  - `UUID requesterId` (must be OWNER to add members)
  - `List<UUID> newMemberIds`

### `LeaveConversationUseCase.java`

- [x] Method: `void leaveConversation(Command command)`
- [x] Inner record `Command`:
  - `UUID conversationId`
  - `UUID userId`

## Notes

- `Message.MessageType` is a public static enum defined inside `Message.java` (from TASK-6.1). Import `com.instagram.domain.model.Message` to reference it.
- `AddGroupMemberUseCase` requires the requester to be an `OWNER` — the service validates this; the in-port just carries the data.
- All seven interfaces go in the `messaging` sub-package. Update imports in `MessagingService` accordingly (TASK-6.5).
