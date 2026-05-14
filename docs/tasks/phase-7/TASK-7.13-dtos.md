# TASK-7.13 — DTOs: Request & Response

## Overview

Create the three DTO records for the notification feature. Follow the same record + factory-method pattern as `MessageResponse.java`.

## Requirements

- Response records live in `adapter/in/web/dto/response/`.
- Request records live in `adapter/in/web/dto/request/`.
- Use Java records.
- Request records use Bean Validation annotations.

## File Locations

```
backend/src/main/java/com/instagram/adapter/in/web/dto/response/NotificationResponse.java
backend/src/main/java/com/instagram/adapter/in/web/dto/request/NotificationSettingsRequest.java
backend/src/main/java/com/instagram/adapter/in/web/dto/request/RegisterDeviceTokenRequest.java
```

---

## Checklist

### `NotificationResponse.java`

- [ ] Java record with fields:
  - `UUID id`
  - `Notification.NotificationType type`
  - `Notification.EntityType entityType`
  - `UUID entityId` (nullable)
  - `String actorUsername` (nullable)
  - `String actorAvatarUrl` (nullable)
  - `boolean isRead`
  - `OffsetDateTime createdAt`
- [ ] Static factory: `from(Notification notification, User actor)`:
  - `actor` may be `null` for system notifications — handle gracefully (`actorUsername = null`, `actorAvatarUrl = null`).
  - Map all `Notification` fields directly.

### `NotificationSettingsRequest.java`

- [ ] Java record with fields:
  - `boolean likesEnabled`
  - `boolean commentsEnabled`
  - `boolean followsEnabled`
  - `boolean messagesEnabled`
  - `boolean pushEnabled`
- [ ] No `@NotNull` needed — Java primitive `boolean` defaults to `false` when the field is omitted from JSON.

### `RegisterDeviceTokenRequest.java`

- [ ] Java record with fields:
  - `@NotBlank String token`
  - `@NotBlank String platform` — expected values: `"FCM"` or `"APNS"`

## Notes

- `NotificationResponse` omits `recipientId` — it is always the current user and is redundant in the response.
- If you want to avoid client-side message formatting, consider adding a computed `String message` field to `NotificationResponse` built from the actor username and notification type. This is optional — the frontend task (TASK-7.18) handles it client-side.
