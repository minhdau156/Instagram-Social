# TASK-7.11 — WebSocket Push Configuration

## Overview

Update `WebSocketConfig` to enable user-destination routing so the server can send messages to individual users via `convertAndSendToUser(...)`. This is required for per-user real-time notification delivery.

## Requirements

- Minimal change — only add `setUserDestinationPrefix`.
- No new files.

## File Location

```
backend/src/main/java/com/instagram/infrastructure/config/WebSocketConfig.java    (modify)
```

---

## Checklist

### `WebSocketConfig.java`

- [ ] In the `configureMessageBroker(MessageBrokerRegistry registry)` method, add one line:
  ```java
  registry.setUserDestinationPrefix("/user");
  ```
- [ ] Verify that the existing `/topic` simple broker and `/app` application-destination prefix are untouched.
- [ ] No other changes to this file.

## Notes

- With this prefix configured, calling `simpMessagingTemplate.convertAndSendToUser("abc-123", "/topic/notifications", payload)` routes the message to the WebSocket session of the user whose principal name is `"abc-123"`.
- The frontend client subscribes to `/user/topic/notifications` — it does **not** include the user ID in the path. Spring resolves the correct user automatically from the authenticated STOMP session's principal.
- This one-line change unblocks `NotificationService.createNotification` (TASK-7.6) which calls `convertAndSendToUser`.
