# TASK-7.2 — Domain Model: NotificationSettings

## Overview

Create the `NotificationSettings` domain model. This holds per-user preferences for which notification types to receive. Because it has no business logic and all fields are required, a Java record is appropriate here instead of the full Builder pattern.

## Requirements

- Lives in `domain/model/` — no Spring, JPA, or Lombok imports.

## File Location

```
backend/src/main/java/com/instagram/domain/model/NotificationSettings.java
```

---

## Checklist

- [x] `userId: UUID` — the owner of these settings.
- [x] `likesEnabled: boolean` — receive LIKE notifications.
- [x] `commentsEnabled: boolean` — receive COMMENT and MENTION notifications.
- [x] `followsEnabled: boolean` — receive FOLLOW and FOLLOW_REQUEST notifications.
- [x] `messagesEnabled: boolean` — receive MESSAGE notifications.
- [x] `pushEnabled: boolean` — master switch for FCM/APNs push delivery; does not affect WebSocket or in-app display.
- [x] Static factory method `defaultsFor(UUID userId)` — returns a `NotificationSettings` instance with all boolean flags set to `true`. Called by the service when a user has no saved settings yet.

## Notes

- This model has no `withX()` mutation methods — settings are always replaced wholesale via `updateSettings`.
- The persistence layer maps this to the `notification_settings` table where `user_id` is the primary key.
