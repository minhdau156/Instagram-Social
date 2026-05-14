# TASK-7.7 — Domain Event Integration

## Overview

Wire notification creation into the existing interaction services (Like, Comment, Follow) using Spring's `ApplicationEvent` mechanism. Existing services publish events; a dedicated `NotificationEventHandler` listens and calls `CreateNotificationUseCase`. This avoids direct coupling between the interaction services and the notification service.

## Requirements

- The event class lives in `domain/event/`.
- The handler lives in `infrastructure/event/`.
- Modify `LikeService`, `CommentService`, and `FollowService` minimally — only inject `ApplicationEventPublisher` and add publish calls.

## File Locations

```
backend/src/main/java/com/instagram/domain/event/NotificationEvent.java                      (new)
backend/src/main/java/com/instagram/infrastructure/event/NotificationEventHandler.java        (new)
backend/src/main/java/com/instagram/domain/service/LikeService.java                           (modify)
backend/src/main/java/com/instagram/domain/service/CommentService.java                        (modify)
backend/src/main/java/com/instagram/domain/service/FollowService.java                         (modify)
```

---

## Checklist

### `NotificationEvent.java`

- [ ] Extend `org.springframework.context.ApplicationEvent`.
- [ ] Fields (all `final`):
  - `Notification.NotificationType type`
  - `UUID recipientId`
  - `UUID actorId` (nullable)
  - `Notification.EntityType entityType`
  - `UUID entityId` (nullable)
- [ ] Constructor: call `super(source)` where `source = this`, then set all fields.
- [ ] Hand-written getters for all fields (no Lombok — this sits in domain layer).

### `NotificationEventHandler.java`

- [ ] `@Component`.
- [ ] Inject `CreateNotificationUseCase` via constructor.
- [ ] Method annotated with `@EventListener` that accepts `NotificationEvent`:
  - Map the event's fields into a `CreateNotificationUseCase.Command`.
  - Call `createNotificationUseCase.createNotification(command)`.
- [ ] Annotate the method with `@Async` so the notification creation does not block the original transaction. Add `@EnableAsync` to a Spring config class if not already present.

### Modify `LikeService`

- [ ] Add `ApplicationEventPublisher eventPublisher` as a new `final` field.
- [ ] Inject it via constructor (add to the existing constructor — do not replace the existing dependencies).
- [ ] After a like is saved successfully, publish a `NotificationEvent`:
  - `type = LIKE`
  - `recipientId` = the post author's user ID (load it from the post or from a `UserRepository` call)
  - `actorId` = the liker's user ID
  - `entityType = POST`
  - `entityId` = the post's ID
- [ ] Guard: do **not** publish if `actorId.equals(recipientId)` — no self-like notifications.

### Modify `CommentService`

- [ ] Add `ApplicationEventPublisher eventPublisher` via constructor.
- [ ] After a comment is saved, publish a `NotificationEvent` with `type = COMMENT`:
  - `recipientId` = the post author's user ID
  - `actorId` = the commenter's user ID
  - `entityType = POST`, `entityId` = the post's ID
- [ ] Guard: skip if commenter == post author.
- [ ] Additionally, scan `comment.content()` for `@username` mentions using the regex `@(\w+)`. For each match, look up the mentioned user by username; if found, publish a separate event with `type = MENTION`, `recipientId` = the mentioned user's ID.

### Modify `FollowService`

- [ ] Add `ApplicationEventPublisher eventPublisher` via constructor.
- [ ] After a follow or follow-request is saved, publish a `NotificationEvent`:
  - `type = FOLLOW` (public account) or `FOLLOW_REQUEST` (private account)
  - `recipientId` = the followed user's ID
  - `actorId` = the follower's ID
  - `entityType = FOLLOW`, `entityId = null`

## Notes

- `ApplicationEventPublisher` is a Spring interface — it is safe to inject into any Spring-managed bean, including domain services.
- `@Async` on the handler method means notification creation runs in a thread pool thread. If the handler throws, the exception does not roll back the original like/comment/follow transaction. This is intentional — a failed notification must not undo a successful interaction.
- Do not publish a notification when a user reacts to their own content (see guards above).
