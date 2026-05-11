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

- [ ] Mock `ConversationRepository`, `MessageRepository`, `SimpMessagingTemplate`.
- [ ] **`createConversation` — 1-to-1 (new):** `findExisting1to1` returns empty, expect `save` called once, both users added as members.
- [ ] **`createConversation` — 1-to-1 (existing):** `findExisting1to1` returns a conversation, expect no `save`, return existing conversation.
- [ ] **`sendMessage` — member check fails:** `isMember` returns false, expect `NotConversationMemberException`.
- [ ] **`sendMessage` — success:** `isMember` returns true, expect `messageRepository.save` called, `simpMessagingTemplate.convertAndSend` called with correct topic.
- [ ] **`getMessages` — non-member:** expect `NotConversationMemberException`.
- [ ] **`markRead` — delegates correctly:** verify `markAsRead` called with correct args.
- [ ] **`leaveConversation` — non-member:** expect `NotConversationMemberException`.

### `ConversationPersistenceAdapterIT.java` (`@DataJpaTest`)

- [ ] **`save` then `findById`:** saves a `Conversation`, retrieves it, asserts fields match.
- [ ] **`addMember` then `isMember`:** adds a member, verifies `isMember` returns true.
- [ ] **`removeMember` then `isMember`:** removes a member, verifies `isMember` returns false.
- [ ] **`findExisting1to1`:** creates a 1-to-1 conversation with two members, asserts it is found by both user IDs.
- [ ] **`findByMemberId`:** user is member of two conversations, asserts both are returned ordered by `updatedAt` DESC.

### `MessageControllerTest.java` (`@WebMvcTest`)

- [ ] Mock all seven use-case interfaces.
- [ ] **`GET /api/v1/conversations` — 200:** authenticated user, mock returns two conversations, assert JSON array length.
- [ ] **`POST /api/v1/conversations` — 201:** valid body, assert `createConversation` called with correct command.
- [ ] **`POST /api/v1/conversations` — 400:** missing `participantIds`, assert validation error.
- [ ] **`GET /api/v1/conversations/{id}/messages` — 200:** mock returns messages list.
- [ ] **`POST /api/v1/conversations/{id}/messages` — 201:** valid `SendMessageRequest`, assert `sendMessage` called.
- [ ] **`PUT /api/v1/conversations/{id}/read` — 204:** assert `markRead` called.
- [ ] **`GET /api/v1/conversations/{id}/messages` — 403:** `getMessages` throws `NotConversationMemberException`, assert 403 response.

## Notes

- For `@WebMvcTest`, mock the authenticated user the same way existing controller tests do (check `PostControllerTest` or `FeedControllerTest` for the pattern).
- `@DataJpaTest` loads only JPA slice — you may need to provide the Flyway migration or use `ddl-auto: create-drop` for the test schema.
- Keep test data builders (`buildConversation()`, `buildMessage()`) as private static helper methods — do not create separate test fixture classes.
