# TASK-7.5 — In-Ports (Use-Case Interfaces)

## Overview

Create six use-case interfaces (in-ports) for the notification feature. One interface per use case, one method per interface. Each has an inner `Command` or `Query` record for its input. Follow the exact same structure as the messaging in-ports (TASK-6.4).

## Requirements

- Lives in `domain/port/in/notification/` — mirrors the `messaging` sub-package pattern.
- Pure Java — no framework imports.

## File Locations

```
backend/src/main/java/com/instagram/domain/port/in/notification/GetNotificationsUseCase.java
backend/src/main/java/com/instagram/domain/port/in/notification/MarkNotificationReadUseCase.java
backend/src/main/java/com/instagram/domain/port/in/notification/MarkAllNotificationsReadUseCase.java
backend/src/main/java/com/instagram/domain/port/in/notification/GetNotificationSettingsUseCase.java
backend/src/main/java/com/instagram/domain/port/in/notification/UpdateNotificationSettingsUseCase.java
backend/src/main/java/com/instagram/domain/port/in/notification/CreateNotificationUseCase.java
```

---

## Checklist

### `GetNotificationsUseCase.java`

- [ ] Method: `List<Notification> getNotifications(Query query)`
- [ ] Inner record: `Query(UUID userId, int page, int size)`

### `MarkNotificationReadUseCase.java`

- [ ] Method: `void markRead(Command command)`
- [ ] Inner record: `Command(UUID notificationId, UUID userId)` — `userId` is passed to verify the notification belongs to this user before marking it.

### `MarkAllNotificationsReadUseCase.java`

- [ ] Method: `void markAllRead(Command command)`
- [ ] Inner record: `Command(UUID userId)`

### `GetNotificationSettingsUseCase.java`

- [ ] Method: `NotificationSettings getSettings(Query query)`
- [ ] Inner record: `Query(UUID userId)`

### `UpdateNotificationSettingsUseCase.java`

- [ ] Method: `NotificationSettings updateSettings(Command command)`
- [ ] Inner record: `Command(UUID userId, boolean likesEnabled, boolean commentsEnabled, boolean followsEnabled, boolean messagesEnabled, boolean pushEnabled)`

### `CreateNotificationUseCase.java`

- [ ] Method: `Notification createNotification(Command command)` — internal trigger; not exposed via the REST controller directly.
- [ ] Inner record: `Command(Notification.NotificationType type, UUID recipientId, UUID actorId, Notification.EntityType entityType, UUID entityId)`

## Notes

- `CreateNotificationUseCase` is called by `NotificationEventHandler` (TASK-7.7) — not by the controller. It exists as a use-case interface so the handler stays decoupled from the concrete service.
- `RegisterDeviceTokenUseCase` is omitted — device token registration is a thin CRUD operation handled directly in the controller without a use-case interface.
