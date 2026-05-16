# TASK-7.16 — API Service: notificationsApi.ts

## Overview

Create the Axios-based API service for the notification feature. Follow the exact same pattern as `messagingApi.ts` — one file per domain, all functions async and typed.

## Requirements

- Use the shared `api` Axios instance from `./client` — it already attaches the JWT `Authorization` header via interceptor.
- Never call `fetch` directly.
- All functions return typed data.

## File Location

```
frontend/src/api/notificationsApi.ts
```

---

## Checklist

- [x] Import `api` from `'./client'`.
- [x] Import `Notification`, `NotificationSettings`, `RegisterDeviceTokenPayload` from `'../types/notification'`.
- [x] Define `const BASE_URL = '/api/v1/notifications'`.
- [x] Export `notificationsApi` object with these methods:

  **`getNotifications(page = 0, size = 20): Promise<Notification[]>`**
  - `GET /api/v1/notifications?page={page}&size={size}`
  - Unwrap with `.then(r => r.data.data)` — the server wraps the list in `{ data: [...] }`.

  **`markRead(id: string): Promise<void>`**
  - `PUT /api/v1/notifications/{id}/read`
  - No response body needed — `.then(() => {})` or return the promise directly.

  **`markAllRead(): Promise<void>`**
  - `PUT /api/v1/notifications/read-all`

  **`getSettings(): Promise<NotificationSettings>`**
  - `GET /api/v1/notifications/settings`
  - Unwrap with `.then(r => r.data.data)`.

  **`updateSettings(settings: NotificationSettings): Promise<NotificationSettings>`**
  - `PUT /api/v1/notifications/settings`
  - Body: pass `settings` as the request body.
  - Unwrap with `.then(r => r.data.data)`.

  **`registerDeviceToken(payload: RegisterDeviceTokenPayload): Promise<void>`**
  - `POST /api/v1/device-tokens`

## Notes

- The `api` Axios instance returns `AxiosResponse<ApiResponse<T>>`. The server wraps everything in `{ data: T, error: null }`, so you need `.then(r => r.data.data)` to get `T` — do this consistently across all methods that return a body.
- For `void` methods (`markRead`, `markAllRead`, `registerDeviceToken`), you can just return the promise from `api.put(...)` or `api.post(...)` without unwrapping.
