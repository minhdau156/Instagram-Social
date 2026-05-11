# TASK-6.6 — JPA Entities

## Overview

Create four JPA entities mapping to the `conversations`, `conversation_members`, `messages`, and `message_reads` tables. Reference `docs/database/schema.sql` for exact column names, types, and constraints.

## Requirements

- Lives in `adapter/out/persistence/`.
- Lombok is allowed: `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.
- Use `@Table(name = "...")` to map explicitly to the DB table name.
- Composite primary keys use `@EmbeddedId` with a separate `@Embeddable` ID class.

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/ConversationJpaEntity.java
backend/src/main/java/com/instagram/adapter/out/persistence/ConversationMemberJpaEntity.java
backend/src/main/java/com/instagram/adapter/out/persistence/ConversationMemberId.java
backend/src/main/java/com/instagram/adapter/out/persistence/MessageJpaEntity.java
backend/src/main/java/com/instagram/adapter/out/persistence/MessageReadJpaEntity.java
backend/src/main/java/com/instagram/adapter/out/persistence/MessageReadId.java
```

---

## Checklist

### `ConversationJpaEntity.java`

- [ ] `@Entity @Table(name = "conversations")`
- [ ] `@Id @GeneratedValue` UUID `id` with `@Column(columnDefinition = "uuid", updatable = false)`
- [ ] `String name` (nullable)
- [ ] `boolean isGroup` mapped to column `is_group`
- [ ] `OffsetDateTime createdAt` mapped to `created_at`
- [ ] `OffsetDateTime updatedAt` mapped to `updated_at`

### `ConversationMemberId.java` (embeddable composite key)

- [ ] `@Embeddable` class with `@Getter @NoArgsConstructor @AllArgsConstructor @EqualsAndHashCode`
- [ ] `UUID conversationId`
- [ ] `UUID userId`
- [ ] Both fields annotated with `@Column(columnDefinition = "uuid")`

### `ConversationMemberJpaEntity.java`

- [ ] `@Entity @Table(name = "conversation_members")`
- [ ] `@EmbeddedId ConversationMemberId id`
- [ ] `@Enumerated(EnumType.STRING) ConversationMember.Role role`
- [ ] `OffsetDateTime joinedAt` mapped to `joined_at`

### `MessageJpaEntity.java`

- [ ] `@Entity @Table(name = "messages")`
- [ ] `@Id @GeneratedValue` UUID `id`
- [ ] `UUID conversationId` mapped to `conversation_id`
- [ ] `UUID senderId` mapped to `sender_id`
- [ ] `String content` (nullable)
- [ ] `@Enumerated(EnumType.STRING) Message.MessageType messageType` mapped to `message_type`
- [ ] `String mediaUrl` mapped to `media_url` (nullable)
- [ ] `UUID sharedPostId` mapped to `shared_post_id` (nullable)
- [ ] `@Enumerated(EnumType.STRING) Message.MessageStatus status`
- [ ] `OffsetDateTime createdAt` mapped to `created_at`

### `MessageReadId.java` (embeddable composite key)

- [ ] `@Embeddable` with `conversationId` and `userId` — same pattern as `ConversationMemberId`

### `MessageReadJpaEntity.java`

- [ ] `@Entity @Table(name = "message_reads")`
- [ ] `@EmbeddedId MessageReadId id`
- [ ] `OffsetDateTime readAt` mapped to `read_at`

## Notes

- Verify exact column names and nullable constraints against `docs/database/schema.sql` before finalising.
- Do **not** add `@OneToMany` / `@ManyToOne` associations between entities — keep them flat. The persistence adapters join manually via JPQL when needed.
- Enums (`Message.MessageType`, `Message.MessageStatus`, `ConversationMember.Role`) are defined in the domain model (TASK-6.1). Import from `com.instagram.domain.model.*`.
