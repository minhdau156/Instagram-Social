# TASK-6.5 — Domain Service: MessagingService

## Overview

Create `MessagingService` — the single domain service that implements all seven in-ports from TASK-6.4. It orchestrates conversation and message operations using the two out-ports from TASK-6.3, and pushes WebSocket events after a message is sent.

## Requirements

- Lives in `domain/service/` — **no** JPA annotations, but Spring's `@Service` and `@Transactional` are allowed here (application-layer concern).
- Actually, per coding standard, put this in `application/service/` since it needs to call infrastructure (WebSocket). Follow the precedent set by `UserInterestService`.
- Constructor injection only. All dependencies are `final`.
- Inject `ConversationRepository`, `MessageRepository`, and `SimpMessagingTemplate` (for WebSocket broadcast).

## File Location

```
backend/src/main/java/com/instagram/application/service/MessagingService.java
```

---

## Checklist

### Class declaration

- [x] `@Service` annotation.
- [x] Implements all seven use-case interfaces from TASK-6.4.
- [x] Constructor injects: `ConversationRepository`, `MessageRepository`, `SimpMessagingTemplate`.

### `createConversation`

- [x] For 1-to-1 (`isGroup = false`): call `conversationRepository.findExisting1to1(creatorId, participantId)`. If found, return the existing conversation — do not create a duplicate.
- [x] For new conversations: build a `Conversation` via its Builder, call `conversationRepository.save(...)`.
- [x] Add creator as `OWNER` via `conversationRepository.addMember(...)`.
- [x] Add all `participantIds` as `MEMBER` via `conversationRepository.addMember(...)`.
- [x] Return the saved `Conversation`.

### `getConversations`

- [x] Call `conversationRepository.findByMemberId(userId, PageRequest.of(page, size))`.
- [x] Return the list as-is.

### `getMessages`

- [x] Verify `requesterId` is a member: call `conversationRepository.isMember(conversationId, requesterId)`. If not, throw `NotConversationMemberException`.
- [x] Fetch: `messageRepository.findByConversationId(conversationId, cursor, limit)`.
- [x] Return the list.

### `sendMessage`

- [x] Verify `senderId` is a member; throw `NotConversationMemberException` if not.
- [x] Build a `Message` via its Builder with `status = SENT` and `createdAt = OffsetDateTime.now()`.
- [x] Save via `messageRepository.save(...)`.
- [x] Broadcast to `/topic/conversations/{conversationId}` using `simpMessagingTemplate.convertAndSend(...)`.
- [x] Return the saved `Message`.

### `markRead`

- [x] Call `messageRepository.markAsRead(conversationId, userId, OffsetDateTime.now())`.

### `addGroupMember`

- [x] Load conversation: `conversationRepository.findById(conversationId)` — throw `ConversationNotFoundException` if absent.
- [x] Verify `requesterId` is a member and is `OWNER` — throw `NotConversationMemberException` if not. (To check role, you may need to add a `findMember(UUID conversationId, UUID userId): Optional<ConversationMember>` method to `ConversationRepository` — add it if needed.)
- [x] Add each `newMemberId` as `MEMBER`.

### `leaveConversation`

- [x] Verify `userId` is a member; throw `NotConversationMemberException` if not.
- [x] Call `conversationRepository.removeMember(conversationId, userId)`.

## Notes

- `SimpMessagingTemplate` is a Spring class — injecting it here is the accepted pattern for pushing real-time events from the service layer.
- The `@Transactional` annotation should be placed on methods that perform multiple writes (e.g., `createConversation` which saves a conversation + adds members).
- For `createConversation`, if `isGroup = false`, ensure `participantIds` has exactly one element (the other party). Throw `IllegalArgumentException` if not.
