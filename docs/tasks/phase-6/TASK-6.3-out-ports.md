# TASK-6.3 — Out-Ports: ConversationRepository & MessageRepository

## Overview

Define the two driven ports that abstract all messaging data access. The domain service depends only on these interfaces — never on JPA directly.

## Requirements

- Lives in `domain/port/out/` — **no** Spring or JPA imports.
- Uses only domain types (`Conversation`, `ConversationMember`, `Message`) and primitive Java types.
- `Pageable` from `org.springframework.data.domain` is **allowed** on out-ports (it is a data-contract type, not a framework concern).

## File Locations

```
backend/src/main/java/com/instagram/domain/port/out/ConversationRepository.java
backend/src/main/java/com/instagram/domain/port/out/MessageRepository.java
```

---

## Checklist

### `ConversationRepository.java`

- [ ] `Conversation save(Conversation conversation)`
- [ ] `Optional<Conversation> findById(UUID conversationId)`
- [ ] `List<Conversation> findByMemberId(UUID userId, Pageable pageable)` — ordered by latest message descending
- [ ] `void addMember(UUID conversationId, UUID userId, ConversationMember.Role role)`
- [ ] `void removeMember(UUID conversationId, UUID userId)`
- [ ] `boolean isMember(UUID conversationId, UUID userId)`
- [ ] `Optional<Conversation> findExisting1to1(UUID userId1, UUID userId2)` — returns existing 1-to-1 conversation if it exists

### `MessageRepository.java`

- [ ] `Message save(Message message)`
- [ ] `Optional<Message> findById(UUID messageId)`
- [ ] `List<Message> findByConversationId(UUID conversationId, UUID cursor, int limit)` — cursor-paginated, newest first; `cursor = null` returns the latest page
- [ ] `void markAsRead(UUID conversationId, UUID userId, OffsetDateTime readAt)`
- [ ] `int getUnreadCount(UUID conversationId, UUID userId)`

## Notes

- Cursor pagination: `cursor` is the `id` of the last message the client already has. The query returns messages **older** than the cursor. This lets the user scroll upward to load history.
- `findByMemberId` is used for the conversation list (inbox); ordering by latest message time is handled in the persistence adapter via a JPQL join.
- Keep imports: `java.util.List`, `java.util.Optional`, `java.util.UUID`, `java.time.OffsetDateTime`, `org.springframework.data.domain.Pageable`, and domain model imports.
