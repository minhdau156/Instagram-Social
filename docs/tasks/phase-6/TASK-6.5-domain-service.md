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

- [ ] `@Service` annotation.
- [ ] Implements all seven use-case interfaces from TASK-6.4.
- [ ] Constructor injects: `ConversationRepository`, `MessageRepository`, `SimpMessagingTemplate`.

### `createConversation`

- [ ] For 1-to-1 (`isGroup = false`): call `conversationRepository.findExisting1to1(creatorId, participantId)`. If found, return the existing conversation — do not create a duplicate.
- [ ] For new conversations: build a `Conversation` via its Builder, call `conversationRepository.save(...)`.
- [ ] Add creator as `OWNER` via `conversationRepository.addMember(...)`.
- [ ] Add all `participantIds` as `MEMBER` via `conversationRepository.addMember(...)`.
- [ ] Return the saved `Conversation`.

### `getConversations`

- [ ] Call `conversationRepository.findByMemberId(userId, PageRequest.of(page, size))`.
- [ ] Return the list as-is.

### `getMessages`

- [ ] Verify `requesterId` is a member: call `conversationRepository.isMember(conversationId, requesterId)`. If not, throw `NotConversationMemberException`.
- [ ] Fetch: `messageRepository.findByConversationId(conversationId, cursor, limit)`.
- [ ] Return the list.

### `sendMessage`

- [ ] Verify `senderId` is a member; throw `NotConversationMemberException` if not.
- [ ] Build a `Message` via its Builder with `status = SENT` and `createdAt = OffsetDateTime.now()`.
- [ ] Save via `messageRepository.save(...)`.
- [ ] Broadcast to `/topic/conversations/{conversationId}` using `simpMessagingTemplate.convertAndSend(...)`.
- [ ] Return the saved `Message`.

### `markRead`

- [ ] Call `messageRepository.markAsRead(conversationId, userId, OffsetDateTime.now())`.

### `addGroupMember`

- [ ] Load conversation: `conversationRepository.findById(conversationId)` — throw `ConversationNotFoundException` if absent.
- [ ] Verify `requesterId` is a member and is `OWNER` — throw `NotConversationMemberException` if not. (To check role, you may need to add a `findMember(UUID conversationId, UUID userId): Optional<ConversationMember>` method to `ConversationRepository` — add it if needed.)
- [ ] Add each `newMemberId` as `MEMBER`.

### `leaveConversation`

- [ ] Verify `userId` is a member; throw `NotConversationMemberException` if not.
- [ ] Call `conversationRepository.removeMember(conversationId, userId)`.

## Notes

- `SimpMessagingTemplate` is a Spring class — injecting it here is the accepted pattern for pushing real-time events from the service layer.
- The `@Transactional` annotation should be placed on methods that perform multiple writes (e.g., `createConversation` which saves a conversation + adds members).
- For `createConversation`, if `isGroup = false`, ensure `participantIds` has exactly one element (the other party). Throw `IllegalArgumentException` if not.
