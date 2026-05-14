# TASK-7.3 — Domain Exceptions

## Overview

Add the single domain exception needed for the notifications feature and register it in `GlobalExceptionHandler`.

## Requirements

- Lives in `domain/exception/`.
- Follows the same pattern as `PostNotFoundException` and `ConversationNotFoundException`.

## File Locations

```
backend/src/main/java/com/instagram/domain/exception/NotificationNotFoundException.java    (new)
backend/src/main/java/com/instagram/adapter/in/web/GlobalExceptionHandler.java             (modify)
```

---

## Checklist

### `NotificationNotFoundException.java`

- [ ] Extends `RuntimeException`.
- [ ] Constructor takes `UUID notificationId` and passes a descriptive message to `super(...)`, e.g. `"Notification not found: " + notificationId`.
- [ ] Static factory `withId(UUID id)` — returns `new NotificationNotFoundException(id)`.

### `GlobalExceptionHandler.java`

- [ ] Add a handler method for `NotificationNotFoundException` that returns `404 NOT FOUND` with a standard error body. Mirror the existing handler for `PostNotFoundException`.

## Notes

- No other domain exceptions are needed for this phase. Ownership violations (trying to mark someone else's notification as read) throw a plain `IllegalArgumentException`, which the existing `GlobalExceptionHandler` should already map to `400 BAD REQUEST`.
