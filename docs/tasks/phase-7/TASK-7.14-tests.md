# TASK-7.14 — Unit & Integration Tests

## Overview

Write tests covering the notification service, event handler, persistence adapter, and REST controller. Follow the naming conventions and patterns already established in phases 4 and 6.

## Requirements

- Unit tests: JUnit 5 + Mockito. No Spring context loaded.
- Integration tests: `@DataJpaTest` for persistence, `@SpringBootTest` + `MockMvc` for the controller.
- Aim for ≥ 80% coverage of new code.

## File Locations

```
backend/src/test/java/com/instagram/application/service/NotificationServiceTest.java
backend/src/test/java/com/instagram/infrastructure/event/NotificationEventHandlerTest.java
backend/src/test/java/com/instagram/adapter/out/persistence/NotificationPersistenceAdapterIT.java
backend/src/test/java/com/instagram/adapter/in/web/NotificationControllerIT.java
```

---

## Checklist

### `NotificationServiceTest.java` (unit)

- [ ] Mock all dependencies: `NotificationRepository`, `NotificationSettingsRepository`, `PushNotificationPort`, `UserRepository`, `SimpMessagingTemplate`.
- [ ] `createNotification` — settings allow:
  - [ ] Saves the notification via `notificationRepository.save(...)`.
  - [ ] Calls `simpMessagingTemplate.convertAndSendToUser(...)` after saving.
- [ ] `createNotification` — settings flag is `false` (e.g., `likesEnabled = false` for a LIKE event):
  - [ ] Does **not** call `notificationRepository.save(...)`.
  - [ ] Does **not** call `convertAndSendToUser(...)`.
- [ ] `createNotification` — `pushEnabled = false`:
  - [ ] Does **not** call `pushNotificationPort.sendPush(...)`.
- [ ] `createNotification` — `pushEnabled = true`:
  - [ ] Calls `pushNotificationPort.sendPush(recipientId, title, body)`.
- [ ] `markRead` — notification not found: throws `NotificationNotFoundException`.
- [ ] `markRead` — userId does not match recipientId: throws `IllegalArgumentException`.
- [ ] `markRead` — success: calls `notificationRepository.markAsRead(notificationId)`.
- [ ] `getSettings` — no settings exist: returns `NotificationSettings.defaultsFor(userId)`.

### `NotificationEventHandlerTest.java` (unit)

- [ ] Mock `CreateNotificationUseCase`.
- [ ] Construct a `NotificationEvent` directly and call `handler.onEvent(event)`.
- [ ] Verify `createNotificationUseCase.createNotification(...)` is called with the correct mapped `Command` fields (use `ArgumentCaptor` if needed).

### `NotificationPersistenceAdapterIT.java` (`@DataJpaTest`)

- [ ] Save a notification and reload via `findById` — verify all fields round-trip correctly including enum values.
- [ ] `markAsRead` — verify `isRead` becomes `true` after the call.
- [ ] `markAllAsRead` — insert 3 unread notifications for the same recipient, call `markAllAsRead`, verify `countByRecipientIdAndIsReadFalse` returns 0.
- [ ] `getUnreadCount` — returns correct count before and after marking read.
- [ ] Note: insert a `users` row in `@BeforeEach` to satisfy the foreign key on `recipient_id` and `actor_id`.

### `NotificationControllerIT.java` (`@SpringBootTest` + `MockMvc`)

- [ ] Use `@MockBean` for all use-case interfaces so no real DB is hit.
- [ ] `GET /api/v1/notifications` — authenticated request returns 200 with a list.
- [ ] `PUT /api/v1/notifications/{id}/read` — authenticated request returns 204.
- [ ] `PUT /api/v1/notifications/read-all` — authenticated request returns 204.
- [ ] `GET /api/v1/notifications/settings` — authenticated request returns 200 with settings.
- [ ] `PUT /api/v1/notifications/settings` — valid body returns 200 with updated settings.
- [ ] Unauthenticated request to any endpoint returns 401.

## Notes

- For the persistence IT, use `@Sql` or insert test data manually via `UserJpaRepository` in `@BeforeEach` to satisfy FK constraints.
- For the controller IT, stub use-case methods with `Mockito.when(...).thenReturn(...)` using sensible default domain objects.
