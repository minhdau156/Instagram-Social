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

- [ ] Public static enum `NotificationType { LIKE, COMMENT, MENTION, FOLLOW, FOLLOW_REQUEST, MESSAGE }`.
- [ ] Public static enum `EntityType { POST, COMMENT, FOLLOW, MESSAGE }`.
- [ ] Fields:
  - `UUID id`
  - `UUID recipientId` — the user receiving the notification
  - `UUID actorId` — the user who triggered the action (nullable for system notifications)
  - `EntityType entityType`
  - `UUID entityId` — the ID of the related entity (nullable for FOLLOW type)
  - `NotificationType type`
  - `boolean isRead`
  - `OffsetDateTime createdAt`
- [ ] Builder with all fields.
- [ ] Business method `withRead()` — returns a **new** `Notification` with `isRead = true`. Does not mutate the original.
- [ ] No setters.

## Notes

- The model is intentionally flat — no nested objects. The service layer resolves actor username/avatar from `UserRepository` when building the response.
- `actorId` is nullable — treat it that way in the Builder and in mapper code.
- `entityId` is nullable for `FOLLOW` / `FOLLOW_REQUEST` notifications (there is no single entity, the actor's profile is the context).
