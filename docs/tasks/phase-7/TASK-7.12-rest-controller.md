# TASK-7.12 — REST Controller: NotificationController

## Overview

Create `NotificationController` exposing all notification HTTP endpoints. Delegates entirely to use-case interfaces — no business logic in the controller.

## Requirements

- `@RestController @RequestMapping("/api/v1")`.
- `@Tag(name = "Notifications")` for Swagger.
- All endpoints require authentication — covered by the existing `SecurityConfig`.

## File Location

```
backend/src/main/java/com/instagram/adapter/in/web/NotificationController.java
```

---

## Checklist

### Injected dependencies (constructor)

- [ ] `GetNotificationsUseCase`
- [ ] `MarkNotificationReadUseCase`
- [ ] `MarkAllNotificationsReadUseCase`
- [ ] `GetNotificationSettingsUseCase`
- [ ] `UpdateNotificationSettingsUseCase`
- [ ] `GetUserUseCase` — for actor enrichment in the notification list
- [ ] `DeviceTokenJpaRepository` — for device token registration (thin CRUD, no use case needed)

### Endpoints

- [ ] `GET /api/v1/notifications` — paginated notification list for the current user.
  - Params: `@RequestParam(defaultValue = "0") int page`, `@RequestParam(defaultValue = "20") int size`.
  - Call `getNotificationsUseCase.getNotifications(new Query(userId, page, size))`.
  - Batch-fetch actors: collect all distinct non-null `actorId`s from the list, call `getUserUseCase` for each, build a `Map<UUID, User>` for O(1) lookup.
  - Map each `Notification` to `NotificationResponse.from(notification, actorMap.get(notification.getActorId()))`.
  - Return `200 OK` with `ApiResponse<List<NotificationResponse>>`.

- [ ] `PUT /api/v1/notifications/{id}/read` — mark one notification as read.
  - Call `markNotificationReadUseCase.markRead(new Command(notificationId, userId))`.
  - Return `204 NO CONTENT`.

- [ ] `PUT /api/v1/notifications/read-all` — mark all notifications as read.
  - Call `markAllNotificationsReadUseCase.markAllRead(new Command(userId))`.
  - Return `204 NO CONTENT`.

- [ ] `GET /api/v1/notifications/settings` — get current user's notification settings.
  - Call `getNotificationSettingsUseCase.getSettings(new Query(userId))`.
  - Return `200 OK` with `ApiResponse<NotificationSettings>`.

- [ ] `PUT /api/v1/notifications/settings` — update settings.
  - Body: `@Valid @RequestBody NotificationSettingsRequest`.
  - Call `updateNotificationSettingsUseCase.updateSettings(new Command(userId, ...))`.
  - Return `200 OK` with the updated settings.

- [ ] `POST /api/v1/device-tokens` — register a device push token.
  - Body: `@Valid @RequestBody RegisterDeviceTokenRequest`.
  - Build a `DeviceTokenJpaEntity` and call `deviceTokenJpaRepository.save(entity)`.
  - Return `201 CREATED`.

### Path conflict prevention

- [ ] Ensure `PUT /api/v1/notifications/read-all` is declared **above** `PUT /api/v1/notifications/{id}/read` in the class, OR use a more specific path that cannot be mistaken for an `{id}` segment. Spring matches the most specific path first, so `"read-all"` as a literal path takes precedence over `{id}` only if defined correctly.

## Notes

- The actor batch-fetch in `GET /notifications` follows the same pattern used in `MessageController.getConversations()` — collect IDs, fetch in bulk, build a map.
- The `DeviceTokenJpaRepository` injection is a pragmatic exception to the hexagonal rule for a trivial CRUD endpoint. If strict architecture is preferred, add a `RegisterDeviceTokenUseCase` instead.
