# TASK-7.9 — JPA Entities & Repositories

## Overview

Create JPA entities and Spring Data repositories for the `notifications` and `notification_settings` tables. Follow the exact same patterns as phase 6 JPA entities.

## Requirements

- Check `docs/database/schema.sql` for exact column names, types, and constraints before coding.
- Lombok allowed: `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.
- `@Table(name = ...)` must match the schema exactly.
- `@Enumerated(EnumType.STRING)` on all enum fields — never `ORDINAL`.

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/entity/NotificationJpaEntity.java
backend/src/main/java/com/instagram/adapter/out/persistence/repository/NotificationJpaRepository.java
backend/src/main/java/com/instagram/adapter/out/persistence/entity/NotificationSettingsJpaEntity.java
backend/src/main/java/com/instagram/adapter/out/persistence/repository/NotificationSettingsJpaRepository.java
```

---

## Checklist

### `NotificationJpaEntity.java`

- [ ] `@Entity @Table(name = "notifications")`.
- [ ] Fields (verify names against schema):
  - `@Id UUID id` — `@GeneratedValue(strategy = GenerationType.UUID)`
  - `UUID recipientId`
  - `UUID actorId` (nullable — `@Column(nullable = true)`)
  - `@Enumerated(EnumType.STRING) Notification.EntityType entityType`
  - `UUID entityId` (nullable)
  - `@Enumerated(EnumType.STRING) Notification.NotificationType type`
  - `boolean isRead`
  - `OffsetDateTime createdAt`
- [ ] `@PrePersist` sets `createdAt = OffsetDateTime.now()` and `isRead = false` if null.

### `NotificationJpaRepository.java`

- [ ] Extend `JpaRepository<NotificationJpaEntity, UUID>`.
- [ ] `Page<NotificationJpaEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable)`.
- [ ] `@Modifying @Query("UPDATE NotificationJpaEntity n SET n.isRead = true WHERE n.id = :id") void markAsRead(@Param("id") UUID id)`.
- [ ] `@Modifying @Query("UPDATE NotificationJpaEntity n SET n.isRead = true WHERE n.recipientId = :recipientId") void markAllAsRead(@Param("recipientId") UUID recipientId)`.
- [ ] `long countByRecipientIdAndIsReadFalse(UUID recipientId)`.

### `NotificationSettingsJpaEntity.java`

- [ ] `@Entity @Table(name = "notification_settings")`.
- [ ] Fields:
  - `@Id UUID userId` — no auto-generation; `userId` IS the primary key (one row per user).
  - `boolean likesEnabled`
  - `boolean commentsEnabled`
  - `boolean followsEnabled`
  - `boolean messagesEnabled`
  - `boolean pushEnabled`

### `NotificationSettingsJpaRepository.java`

- [ ] Extend `JpaRepository<NotificationSettingsJpaEntity, UUID>`.
- [ ] No extra methods needed — `findById(userId)` and `save(entity)` from `JpaRepository` are sufficient.

## Notes

- `@Modifying` queries need `@Transactional` on the calling service method. The `NotificationService` in TASK-7.6 handles this.
- `NotificationSettingsJpaEntity` uses `userId` as both the Java field name and the primary key — Spring Data will use `user_id` as the column name by default (snake case). Verify this matches the schema; add `@Column(name = "user_id")` if needed.
