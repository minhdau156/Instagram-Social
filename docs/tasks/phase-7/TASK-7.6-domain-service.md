# TASK-7.6 — Domain Service: NotificationService

## Overview

Create `NotificationService` — the single application service that implements all six notification use-case in-ports. It saves notifications, checks user preferences, pushes events via WebSocket, and optionally via FCM.

## Requirements

- Lives in `application/service/` (same reason as `MessagingService` — it needs `SimpMessagingTemplate`).
- `@Service`, constructor injection only, all dependencies `final`.
- Implements all use-case interfaces from TASK-7.5.

## File Location

```
backend/src/main/java/com/instagram/application/service/NotificationService.java
```

---

## Checklist

### Dependencies to inject

- [ ] `NotificationRepository`
- [ ] `NotificationSettingsRepository`
- [ ] `PushNotificationPort`
- [ ] `UserRepository` — to look up actor username for FCM message text
- [ ] `SimpMessagingTemplate` — to push real-time notifications via WebSocket

### `createNotification`

- [ ] `@Transactional`.
- [ ] Load the recipient's `NotificationSettings` via `notificationSettingsRepository.findByUserId(recipientId)`. If absent, treat as `NotificationSettings.defaultsFor(recipientId)` — all enabled.
- [ ] Check the relevant flag based on `command.type()`:
  - `LIKE` → `likesEnabled`
  - `COMMENT` / `MENTION` → `commentsEnabled`
  - `FOLLOW` / `FOLLOW_REQUEST` → `followsEnabled`
  - `MESSAGE` → `messagesEnabled`
- [ ] If the flag is `false`, return early without saving or pushing.
- [ ] Build a `Notification` via its Builder with `isRead = false` and `createdAt = OffsetDateTime.now()`.
- [ ] Save via `notificationRepository.save(...)`.
- [ ] Push to the recipient via `simpMessagingTemplate.convertAndSendToUser(recipientId.toString(), "/topic/notifications", savedNotification)`.
- [ ] If `settings.pushEnabled()` is `true`, look up the actor's username from `UserRepository` and call `pushNotificationPort.sendPush(recipientId, title, body)` with a human-readable string (e.g. `"John liked your photo"`).
- [ ] Return the saved `Notification`.

### `getNotifications`

- [ ] Call `notificationRepository.findByRecipientId(query.userId(), PageRequest.of(query.page(), query.size()))`.
- [ ] Return the list.

### `markRead`

- [ ] Load notification via `notificationRepository.findById(command.notificationId())`. Throw `NotificationNotFoundException.withId(...)` if absent.
- [ ] Verify `notification.getRecipientId().equals(command.userId())`. If not, throw `IllegalArgumentException("not your notification")`.
- [ ] Call `notificationRepository.markAsRead(command.notificationId())`.

### `markAllRead`

- [ ] `@Transactional`.
- [ ] Call `notificationRepository.markAllAsRead(command.userId())`.

### `getSettings`

- [ ] Return `notificationSettingsRepository.findByUserId(query.userId()).orElse(NotificationSettings.defaultsFor(query.userId()))`.

### `updateSettings`

- [ ] `@Transactional`.
- [ ] Build a `NotificationSettings` from the command fields.
- [ ] Save and return via `notificationSettingsRepository.save(...)`.

## Notes

- `convertAndSendToUser` requires `/user` as the user-destination prefix in `WebSocketConfig` — configured in TASK-7.11. The client subscribes to `/user/topic/notifications` (without the user ID); Spring routes it server-side.
- The FCM push text for `createNotification` is a simple `switch` on `command.type()`. Look up the actor's username before formatting.
- Do not call `pushNotificationPort.sendPush` if `actorId` is null (system notification).
