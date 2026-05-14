# TASK-7.1 — Domain Model: Notification

## Overview

Create the core `Notification` domain model. Follow the same hand-written Builder pattern used in `Post.java` and `Message.java` — no Lombok, no framework annotations.

## Requirements

- Lives in `domain/model/` — no Spring, JPA, or Lombok imports.
- Use `Post.java` as the reference for the Builder pattern and immutable-copy behaviour.
- `NotificationType` and `EntityType` are public static enums defined inside `Notification.java`.

## File Location

```
backend/src/main/java/com/instagram/domain/model/Notification.java
```

---

## Checklist

### `Notification.java`

- [x] Public static enum `NotificationType` — 12 values matching `schema.sql` (`LIKE_POST`, `LIKE_COMMENT`, `COMMENT_POST`, `REPLY_COMMENT`, `FOLLOW`, `FOLLOW_REQUEST`, `FOLLOW_ACCEPTED`, `MENTION_POST`, `MENTION_COMMENT`, `DIRECT_MESSAGE`, `GROUP_MESSAGE`, `POST_SHARED`). Spec listed 6 simplified names; schema is source of truth.
- [x] Public static enum `EntityType { POST, COMMENT, FOLLOW, MESSAGE }`.
- [x] Fields:
  - `UUID id`
  - `UUID recipientId` — the user receiving the notification
  - `UUID actorId` — the user who triggered the action (nullable for system notifications)
  - `EntityType entityType`
  - `UUID entityId` — the ID of the related entity (nullable for FOLLOW type)
  - `NotificationType type`
  - `boolean isRead`
  - `OffsetDateTime createdAt`
- [x] Builder with all fields.
- [ ] Business method `withRead()` — returns a **new** `Notification` with `isRead = true`. Does not mutate the original. ⚠️ Currently `withIsRead(boolean)` returning `Builder` — fix: rename to `withRead()`, remove param, return `copy().isRead(true).build()`
- [x] No setters.

## Notes

- The model is intentionally flat — no nested objects. The service layer resolves actor username/avatar from `UserRepository` when building the response.
- `actorId` is nullable — treat it that way in the Builder and in mapper code.
- `entityId` is nullable for `FOLLOW` / `FOLLOW_REQUEST` notifications (there is no single entity, the actor's profile is the context).
