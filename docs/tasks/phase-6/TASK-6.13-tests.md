# TASK-6.13 — Unit & Integration Tests

## Overview

Write tests for the three main layers of the Direct Messaging feature: domain service (unit), persistence adapter (integration), and REST controller (integration).

## Requirements

- Unit tests: JUnit 5 + Mockito. No Spring context.
- Persistence tests: `@DataJpaTest` with an in-memory or Testcontainers PostgreSQL.
- Controller tests: `@WebMvcTest` + `MockMvc`.
- Test class naming: `MessagingServiceTest` (unit), `ConversationPersistenceAdapterIT` (integration), `MessageControllerTest` (controller).
- Target ≥ 80% line coverage on the new classes.

## File Locations

```
backend/src/test/java/com/instagram/domain/service/MessagingServiceTest.java
backend/src/test/java/com/instagram/adapter/out/persistence/ConversationPersistenceAdapterIT.java
backend/src/test/java/com/instagram/adapter/in/web/MessageControllerTest.java
```

---

## Checklist

### `MessagingServiceTest.java` (unit — Mockito)

- [x] Mock `ConversationRepository`, `MessageRepository`, `SimpMessagingTemplate`.
- [x] **`createConversation` — 1-to-1 (new):** `findExisting1to1` returns empty, expect `save` called once, both users added as members.
- [x] **`createConversation` — 1-to-1 (existing):** `findExisting1to1` returns a conversation, expect no `save`, return existing conversation.
- [x] **`sendMessage` — member check fails:** `isMember` returns false, expect `NotConversationMemberException`.
- [x] **`sendMessage` — success:** `isMember` returns true, expect `messageRepository.save` called, `simpMessagingTemplate.convertAndSend` called with correct topic.
- [x] **`getMessages` — non-member:** expect `NotConversationMemberException`.
- [x] **`markRead` — delegates correctly:** verify `markAsRead` called with correct args.
- [x] **`leaveConversation` — non-member:** expect `NotConversationMemberException`.

### `ConversationPersistenceAdapterIT.java` (`@DataJpaTest`)

- [x] **`save` then `findById`:** saves a `Conversation`, retrieves it, asserts fields match.
- [x] **`addMember` then `isMember`:** adds a member, verifies `isMember` returns true.
- [x] **`removeMember` then `isMember`:** removes a member, verifies `isMember` returns false.
- [x] **`findExisting1to1`:** creates a 1-to-1 conversation with two members, asserts it is found by both user IDs.
- [x] **`findByMemberId`:** user is member of two conversations, asserts both are returned ordered by `updatedAt` DESC.

### `MessageControllerTest.java` (`@WebMvcTest`)

- [x] Mock all seven use-case interfaces.
- [x] **`GET /api/v1/conversations` — 200:** authenticated user, mock returns two conversations, assert JSON array length.
- [x] **`POST /api/v1/conversations` — 201:** valid body, assert `createConversation` called with correct command.
- [x] **`POST /api/v1/conversations` — 400:** missing `participantIds`, assert validation error.
- [x] **`GET /api/v1/conversations/{id}/messages` — 200:** mock returns messages list.
- [x] **`POST /api/v1/conversations/{id}/messages` — 201:** valid `SendMessageRequest`, assert `sendMessage` called.
- [x] **`PUT /api/v1/conversations/{id}/read` — 204:** assert `markRead` called.
- [x] **`GET /api/v1/conversations/{id}/messages` — 403:** `getMessages` throws `NotConversationMemberException`, assert 403 response.

## Notes

- For `@WebMvcTest`, mock the authenticated user the same way existing controller tests do (check `PostControllerTest` or `FeedControllerTest` for the pattern).
- `@DataJpaTest` loads only JPA slice — you may need to provide the Flyway migration or use `ddl-auto: create-drop` for the test schema.
- Keep test data builders (`buildConversation()`, `buildMessage()`) as private static helper methods — do not create separate test fixture classes.
