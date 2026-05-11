# TASK-6.7 — JPA Repositories

## Overview

Create three Spring Data JPA repositories for conversations, messages, and message reads. Each extends `JpaRepository` and adds custom queries where the derived method name is insufficient.

## Requirements

- Lives in `adapter/out/persistence/`.
- Annotate custom JPQL queries with `@Query`.
- `@Modifying + @Transactional` required on UPDATE/DELETE queries.

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/ConversationJpaRepository.java
backend/src/main/java/com/instagram/adapter/out/persistence/MessageJpaRepository.java
backend/src/main/java/com/instagram/adapter/out/persistence/MessageReadJpaRepository.java
```

---

## Checklist

### `ConversationJpaRepository.java`

- [ ] Extends `JpaRepository<ConversationJpaEntity, UUID>`
- [ ] Custom query — find conversations where user is a member, ordered by `updatedAt` DESC:
  ```sql
  SELECT c FROM ConversationJpaEntity c
  JOIN ConversationMemberJpaEntity m ON m.id.conversationId = c.id
  WHERE m.id.userId = :userId
  ORDER BY c.updatedAt DESC
  ```
  Method signature: `Page<ConversationJpaEntity> findByMemberId(@Param("userId") UUID userId, Pageable pageable)`
- [ ] Custom query — find existing 1-to-1 conversation between two users:
  ```sql
  SELECT c FROM ConversationJpaEntity c
  WHERE c.isGroup = false
    AND EXISTS (SELECT m FROM ConversationMemberJpaEntity m WHERE m.id.conversationId = c.id AND m.id.userId = :userId1)
    AND EXISTS (SELECT m FROM ConversationMemberJpaEntity m WHERE m.id.conversationId = c.id AND m.id.userId = :userId2)
  ```
  Method signature: `Optional<ConversationJpaEntity> findExisting1to1(@Param("userId1") UUID userId1, @Param("userId2") UUID userId2)`
- [ ] Check if a user is a member:
  Method signature: `boolean existsByIdConversationIdAndIdUserId(UUID conversationId, UUID userId)` on `ConversationMemberJpaRepository` (see note below)

### `ConversationMemberJpaRepository.java`

- [ ] Extends `JpaRepository<ConversationMemberJpaEntity, ConversationMemberId>`
- [ ] `boolean existsById_ConversationIdAndId_UserId(UUID conversationId, UUID userId)` — derived method for membership check
- [ ] `Optional<ConversationMemberJpaEntity> findById_ConversationIdAndId_UserId(UUID conversationId, UUID userId)` — for role lookup

### `MessageJpaRepository.java`

- [ ] Extends `JpaRepository<MessageJpaEntity, UUID>`
- [ ] Latest page (no cursor):
  `List<MessageJpaEntity> findByConversationIdOrderByCreatedAtDesc(UUID conversationId, Pageable pageable)`
- [ ] Cursor-based (older messages):
  ```java
  @Query("SELECT m FROM MessageJpaEntity m WHERE m.conversationId = :convId AND m.createdAt < " +
         "(SELECT m2.createdAt FROM MessageJpaEntity m2 WHERE m2.id = :cursor) " +
         "ORDER BY m.createdAt DESC")
  List<MessageJpaEntity> findOlderThanCursor(
      @Param("convId") UUID conversationId,
      @Param("cursor") UUID cursorMessageId,
      Pageable pageable);
  ```

### `MessageReadJpaRepository.java`

- [ ] Extends `JpaRepository<MessageReadJpaEntity, MessageReadId>`
- [ ] Count unread messages for a user in a conversation (messages sent after their last read time):
  ```java
  @Query("SELECT COUNT(m) FROM MessageJpaEntity m " +
         "WHERE m.conversationId = :convId " +
         "AND m.senderId <> :userId " +
         "AND m.createdAt > COALESCE(" +
         "  (SELECT r.readAt FROM MessageReadJpaEntity r WHERE r.id.conversationId = :convId AND r.id.userId = :userId), " +
         "  CAST('1970-01-01' AS java.time.OffsetDateTime))")
  int countUnread(@Param("convId") UUID conversationId, @Param("userId") UUID userId);
  ```
- [ ] Upsert read timestamp — use `@Modifying @Transactional @Query` with a native SQL `INSERT ... ON CONFLICT DO UPDATE`:
  ```sql
  INSERT INTO message_reads (conversation_id, user_id, read_at)
  VALUES (:convId, :userId, :readAt)
  ON CONFLICT (conversation_id, user_id) DO UPDATE SET read_at = EXCLUDED.read_at
  ```

## Notes

- `ConversationMemberJpaRepository` is an additional repository not explicitly listed in TASK-6.3's out-port but needed by the persistence adapter for membership checks.
- The `findOlderThanCursor` query sub-selects the cursor message's `createdAt` — this avoids a self-join and is efficient when `messages.id` is indexed.
- Expose `ConversationMemberJpaRepository` only within the `persistence` package — inject it into the adapters, never pass it to the domain.
