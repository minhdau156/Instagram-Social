# TASK-7.4 — Out-Ports

## Overview

Define three out-port interfaces for the notifications domain: a notification repository, a settings repository, and an abstract push notification port. These live in `domain/port/out/` and are implemented by adapters in later tasks.

## Requirements

- Pure Java interfaces — no Spring, JPA, or Lombok imports.
- Method signatures use domain types only (no JPA entities).

## File Locations

```
backend/src/main/java/com/instagram/domain/port/out/NotificationRepository.java
backend/src/main/java/com/instagram/domain/port/out/NotificationSettingsRepository.java
backend/src/main/java/com/instagram/domain/port/out/PushNotificationPort.java
```

---

## Checklist

### `NotificationRepository.java`

- [ ] `Notification save(Notification notification)`
- [ ] `List<Notification> findByRecipientId(UUID recipientId, Pageable pageable)` — ordered newest-first
- [ ] `Optional<Notification> findById(UUID notificationId)`
- [ ] `void markAsRead(UUID notificationId)`
- [ ] `void markAllAsRead(UUID recipientId)`
- [ ] `long getUnreadCount(UUID recipientId)`

### `NotificationSettingsRepository.java`

- [ ] `Optional<NotificationSettings> findByUserId(UUID userId)`
- [ ] `NotificationSettings save(NotificationSettings settings)`

### `PushNotificationPort.java`

- [ ] `void sendPush(UUID userId, String title, String body)` — abstraction over FCM/APNs; the adapter logs for now.
- [ ] No other methods.

## Notes

- `findByRecipientId` takes `Pageable` from `org.springframework.data.domain` — importing this in the domain port is an accepted pragmatic trade-off already used in `ConversationRepository`.
- `PushNotificationPort` is a driven port (out-port): the domain service calls it, and the `FcmPushAdapter` implements it (TASK-7.8).
