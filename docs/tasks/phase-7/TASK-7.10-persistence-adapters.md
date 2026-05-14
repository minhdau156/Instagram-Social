# TASK-7.10 — Persistence Adapters

## Overview

Create two persistence adapters that implement the out-ports from TASK-7.4. Each adapter maps between domain objects and JPA entities using private `toEntity()` / `toDomain()` methods.

## Requirements

- `@Component`. Implements the respective out-port interface.
- Never expose JPA entities outside the `persistence` package.
- Constructor injection only.

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/NotificationPersistenceAdapter.java
backend/src/main/java/com/instagram/adapter/out/persistence/NotificationSettingsPersistenceAdapter.java
```

---

## Checklist

### `NotificationPersistenceAdapter.java`

- [ ] `@Component`. Implements `NotificationRepository`.
- [ ] Inject `NotificationJpaRepository` via constructor.
- [ ] Implement all methods from the out-port:
  - `save` → call `toEntity(notification)`, `jpaRepo.save(entity)`, return `toDomain(saved)`.
  - `findByRecipientId` → call `jpaRepo.findByRecipientIdOrderByCreatedAtDesc(recipientId, pageable)`, map each with `toDomain`, return as `List`.
  - `findById` → `jpaRepo.findById(id).map(this::toDomain)`.
  - `markAsRead` → `jpaRepo.markAsRead(notificationId)`.
  - `markAllAsRead` → `jpaRepo.markAllAsRead(recipientId)`.
  - `getUnreadCount` → `jpaRepo.countByRecipientIdAndIsReadFalse(recipientId)`.
- [ ] Private `toEntity(Notification n): NotificationJpaEntity` — maps all fields including enums.
- [ ] Private `toDomain(NotificationJpaEntity e): Notification` — maps all fields back to domain object using the Builder.

### `NotificationSettingsPersistenceAdapter.java`

- [ ] `@Component`. Implements `NotificationSettingsRepository`.
- [ ] Inject `NotificationSettingsJpaRepository` via constructor.
- [ ] `findByUserId` → `jpaRepo.findById(userId).map(this::toDomain)`.
- [ ] `save` → `toEntity(settings)`, `jpaRepo.save(entity)`, return `toDomain(saved)`.
- [ ] Private `toEntity` / `toDomain` mapping methods.

## Notes

- `toDomain` for `NotificationJpaEntity` must map the `@Enumerated(STRING)` stored values back to `Notification.NotificationType` and `Notification.EntityType` enum constants. Use `Notification.NotificationType.valueOf(entity.getType().name())` or just pass the enum value directly if the JPA entity already uses the domain enum type.
- If the JPA entity stores enums as the domain enum type directly (which Spring Data supports), the mapping is trivial — just copy the value.
