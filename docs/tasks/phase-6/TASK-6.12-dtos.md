# TASK-6.12 — DTOs: Request & Response

## Overview

Create four DTOs for the Direct Messaging REST API. Use Java records with Bean Validation on request DTOs and a static `from()` factory method on response DTOs.

## Requirements

- Lives in `adapter/in/web/dto/`.
- Request DTOs: Java records with `@NotNull` / `@NotBlank` / `@Size` constraints.
- Response DTOs: Java records with a `public static ResponseType from(DomainType)` factory method.
- No JPA entity or JPA type leaks into any DTO.

## File Locations

```
backend/src/main/java/com/instagram/adapter/in/web/dto/request/CreateConversationRequest.java
backend/src/main/java/com/instagram/adapter/in/web/dto/request/SendMessageRequest.java
backend/src/main/java/com/instagram/adapter/in/web/dto/response/ConversationResponse.java
backend/src/main/java/com/instagram/adapter/in/web/dto/response/MessageResponse.java
```

---

## Checklist

### `CreateConversationRequest.java`

- [ ] Java record.
- [ ] Fields:
  - `@NotNull @Size(min = 1) List<UUID> participantIds`
  - `String name` (nullable — only for group chats)
  - `boolean isGroup`

### `SendMessageRequest.java`

- [ ] Java record.
- [ ] Fields:
  - `String content` (nullable for IMAGE/VIDEO)
  - `@NotNull Message.MessageType messageType`
  - `String mediaUrl` (nullable)
  - `UUID sharedPostId` (nullable)

### `ConversationResponse.java`

- [ ] Java record.
- [ ] Fields:
  - `UUID id`
  - `String name`
  - `boolean isGroup`
  - `MessageResponse lastMessage` (nullable — null for new empty conversations)
  - `int unreadCount`
  - `OffsetDateTime createdAt`
- [ ] Static factory: `public static ConversationResponse from(Conversation conversation, MessageResponse lastMessage, int unreadCount)` — takes the domain model plus pre-computed fields from the service.

### `MessageResponse.java`

- [ ] Java record.
- [ ] Fields:
  - `UUID id`
  - `UUID conversationId`
  - `UUID senderId`
  - `String senderUsername`
  - `String senderAvatarUrl`
  - `String content`
  - `Message.MessageType messageType`
  - `String mediaUrl`
  - `UUID sharedPostId`
  - `Message.MessageStatus status`
  - `OffsetDateTime createdAt`
- [ ] Static factory: `public static MessageResponse from(Message message, String senderUsername, String senderAvatarUrl)` — the service resolves sender info before creating the response.

## Notes

- `senderUsername` and `senderAvatarUrl` are not on the `Message` domain model — the controller (or service) must look them up via a `UserRepository` call before building `MessageResponse`. Keep this lookup in the service, not the controller.
- `ConversationResponse.lastMessage` being `null` is valid — new conversations have no messages yet.
- Import `com.instagram.domain.model.Message` in `SendMessageRequest` to reference `Message.MessageType`.
