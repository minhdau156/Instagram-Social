# TASK-7.15 — TypeScript Types: notification.ts

## Overview

Create the TypeScript type definitions for the notification feature. These must mirror the `NotificationResponse` and `NotificationSettings` backend DTOs exactly so no manual conversion is needed when reading API responses.

## Requirements

- No `any` types. Strict TypeScript throughout.
- Use string literal union types (not TypeScript `enum`) for `NotificationType` and `EntityType` — this keeps them directly comparable to string values returned by the API without conversion.

## File Location

```
frontend/src/types/notification.ts
```

---

## Checklist

- [x] `NotificationType` — string union:
  `'LIKE_POST' | 'LIKE_COMMENT' | 'COMMENT_POST' | 'REPLY_COMMENT' | 'FOLLOW' | 'FOLLOW_REQUEST' | 'FOLLOW_ACCEPTED' | 'MENTION_POST' | 'MENTION_COMMENT' | 'DIRECT_MESSAGE' | 'GROUP_MESSAGE' | 'POST_SHARED'` (full backend enum names used)

- [x] `EntityType` — string union:
  `'POST' | 'COMMENT' | 'FOLLOW' | 'MESSAGE'`

- [x] `Notification` interface — mirrors `NotificationResponse` from the backend:
  - `id: string`
  - `type: NotificationType`
  - `entityType: EntityType`
  - `entityId: string | null`
  - `actorUsername: string | null`
  - `actorAvatarUrl: string | null`
  - `isRead: boolean`
  - `createdAt: string` (ISO 8601 — leave as `string`; parse with `new Date(...)` when formatting)

- [x] `NotificationSettings` interface:
  - `likesEnabled: boolean`
  - `commentsEnabled: boolean`
  - `followsEnabled: boolean`
  - `messagesEnabled: boolean`
  - `pushEnabled: boolean`

- [x] `RegisterDeviceTokenPayload` interface:
  - `token: string`
  - `platform: 'FCM' | 'APNS'`

## Notes

- Use `string` for all UUID fields (`id`, `entityId`) — Axios deserialises them as strings.
- `actorUsername` and `actorAvatarUrl` are `null` for system-generated notifications — the UI must handle this (show a default icon / "System" text).
- `isRead` drives the highlight background in `NotificationItem` — it is a regular `boolean`, not `boolean | null`.
