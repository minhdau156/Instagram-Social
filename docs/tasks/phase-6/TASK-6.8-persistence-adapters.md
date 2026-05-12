# TASK-6.8 — Persistence Adapters

## Overview

Create two persistence adapters that implement the out-ports from TASK-6.3. They are the only classes allowed to touch JPA repositories and entities. All mapping between domain models and JPA entities lives here.

## Requirements

- Lives in `adapter/out/persistence/`.
- Annotate with `@Component` (or `@Repository`).
- Private `toEntity()` and `toDomain()` mapping methods — never expose JPA entities outside this package.
- Constructor injection only.

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/ConversationPersistenceAdapter.java
backend/src/main/java/com/instagram/adapter/out/persistence/MessagePersistenceAdapter.java
```

---

## Checklist

### `ConversationPersistenceAdapter.java`

- [x] `@Component` + `implements ConversationRepository`
- [x] Constructor injects: `ConversationJpaRepository`, `ConversationMemberJpaRepository`
- [x] Implement `save(Conversation)`:
  - Call `toEntity(conversation)`, then `jpaRepo.save(entity)`, then `toDomain(saved)`.
- [x] Implement `findById(UUID)`:
  - Call `jpaRepo.findById(id)` and map with `.map(this::toDomain)`.
- [x] Implement `findByMemberId(UUID, Pageable)`:
  - Call `jpaRepo.findByMemberId(userId, pageable)` and map each entity.
- [x] Implement `addMember(UUID conversationId, UUID userId, ConversationMember.Role role)`:
  - Build `ConversationMemberJpaEntity` with the composite key and save via `memberRepo.save(...)`.
- [x] Implement `removeMember(UUID, UUID)`:
  - Delete by composite key: `memberRepo.deleteById(new ConversationMemberId(conversationId, userId))`.
- [x] Implement `isMember(UUID, UUID)`:
  - Delegate to `memberRepo.existsById_ConversationIdAndId_UserId(...)`.
- [x] Implement `findExisting1to1(UUID, UUID)`:
  - Delegate to `jpaRepo.findExisting1to1(...)` and map result.
- [x] Private `toEntity(Conversation)` — maps domain → JPA.
- [x] Private `toDomain(ConversationJpaEntity)` — maps JPA → domain.
- [x] Private `memberToDomain(ConversationMemberJpaEntity)` — maps member JPA → `ConversationMember`.

### `MessagePersistenceAdapter.java`

- [x] `@Component` + `implements MessageRepository`
- [x] Constructor injects: `MessageJpaRepository`, `MessageReadJpaRepository`
- [x] Implement `save(Message)`:
  - Map to entity, save, map back.
- [x] Implement `findById(UUID)`:
  - `jpaRepo.findById(id).map(this::toDomain)`.
- [x] Implement `findByConversationId(UUID conversationId, UUID cursor, int limit)`:
  - If `cursor == null`: call `findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(0, limit))`.
  - Else: call `findOlderThanCursor(conversationId, cursor, PageRequest.of(0, limit))`.
  - Map results.
- [x] Implement `markAsRead(UUID, UUID, OffsetDateTime)`:
  - Call the native upsert query on `MessageReadJpaRepository`.
- [x] Implement `getUnreadCount(UUID, UUID)`:
  - Delegate to `messageReadRepo.countUnread(...)`.
- [x] Private `toEntity(Message)` and `toDomain(MessageJpaEntity)` mapping methods.

## Notes

- The `toDomain(ConversationJpaEntity)` method maps the JPA entity fields to a `Conversation` built via `Conversation.builder()...build()`.
- Do not perform any business logic in the adapter — only mapping and delegation to the JPA repository.
- The `findByConversationId` cursor branch creates the correct `Pageable` via `PageRequest.of(0, limit)` — there is no offset; the cursor drives the position.
