# TASK-7.8 — Device Token JPA & FCM Push Adapter

## Overview

Add the device token persistence layer and a stub `FcmPushAdapter` that implements `PushNotificationPort`. The stub logs the push payload via SLF4J instead of making real HTTP calls — real FCM integration can be added later.

## Requirements

- JPA entity and repository follow the same patterns as phase 6 (Lombok annotations allowed, `@Table(name = ...)` must match schema).
- `FcmPushAdapter` lives in `adapter/out/push/`.

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/entity/DeviceTokenJpaEntity.java        (new)
backend/src/main/java/com/instagram/adapter/out/persistence/repository/DeviceTokenJpaRepository.java  (new)
backend/src/main/java/com/instagram/adapter/out/push/FcmPushAdapter.java                             (new)
```

---

## Checklist

### `DeviceTokenJpaEntity.java`

- [ ] `@Entity @Table(name = "device_tokens")`.
- [ ] Fields (verify column names against `docs/database/schema.sql`):
  - `@Id UUID id` — `@GeneratedValue(strategy = GenerationType.UUID)`
  - `UUID userId`
  - `String token` — the raw FCM or APNs token string
  - `String platform` — store as `VARCHAR`; expected values `"FCM"` or `"APNS"`
  - `OffsetDateTime createdAt`
- [ ] `@PrePersist` sets `createdAt = OffsetDateTime.now()` when null.
- [ ] Lombok: `@Getter`, `@Setter`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`.

### `DeviceTokenJpaRepository.java`

- [ ] Extend `JpaRepository<DeviceTokenJpaEntity, UUID>`.
- [ ] `List<DeviceTokenJpaEntity> findByUserId(UUID userId)` — retrieves all tokens for a user so `FcmPushAdapter` can send to all their devices.
- [ ] `void deleteByToken(String token)` — for token cleanup when a device unregisters.

### `FcmPushAdapter.java`

- [ ] `@Component`. Implements `PushNotificationPort`.
- [ ] Inject `DeviceTokenJpaRepository` via constructor.
- [ ] `sendPush(UUID userId, String title, String body)`:
  - Load all tokens for `userId` via `findByUserId(userId)`.
  - If no tokens exist, return immediately.
  - For each token, log at INFO level: `log.info("FCM push → token={} title='{}' body='{}'", token.getToken(), title, body)`.
  - Do not make any HTTP calls.

## Notes

- Check `docs/database/schema.sql` for the `device_tokens` table before coding the entity — column names must match exactly.
- Real FCM integration (Firebase Admin SDK) can be wired in later by replacing the log statement with `FirebaseMessaging.getInstance().send(Message.builder()...)`.
