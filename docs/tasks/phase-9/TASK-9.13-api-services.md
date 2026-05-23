# TASK-9.13 — API Services: moderationApi.ts, adminApi.ts

## Overview

Create two Axios-based API service files for the moderation feature. `moderationApi.ts` covers user-facing endpoints. `adminApi.ts` covers admin-only endpoints. Follow the exact pattern established by `notificationsApi.ts` and `searchApi.ts` — one file per domain, all functions async and typed, using the shared `api` Axios instance.

---

## Requirements

- Use the shared `api` Axios instance from `'./client'` — JWT attachment is handled by the interceptor.
- Never use `fetch` directly.
- All functions typed using the interfaces from `moderation.ts`.
- Unwrap the `ApiResponse` envelope with `.then(r => r.data.data)` for endpoints that return a body.
- For void endpoints (`DELETE`, `PUT` with no meaningful response), return the raw promise without unwrapping.

---

## File Locations

```
frontend/src/api/moderationApi.ts
frontend/src/api/adminApi.ts
```

---

## Checklist

### `moderationApi.ts`

- [ ] Import `api` from `'./client'`.
- [ ] Import `Report`, `UserBlock`, `SubmitReportPayload` from `'../types/moderation'`.

#### `submitReport(payload: SubmitReportPayload): Promise<Report>`

- [ ] Sends `POST /api/v1/reports` with `payload` as the request body.
- [ ] Unwraps the response with `.then(r => r.data.data)`.
- [ ] Returns `Promise<Report>`.
- [ ] Note: `payload.entityId` is a `string` (UUID). Do not add any conversion.

#### `blockUser(username: string): Promise<void>`

- [ ] Sends `POST /api/v1/users/{username}/block` with no request body.
- [ ] Returns a `Promise<void>`. Use `.then(() => undefined)` to discard the response body.

#### `unblockUser(username: string): Promise<void>`

- [ ] Sends `DELETE /api/v1/users/{username}/block`.
- [ ] Returns `Promise<void>`.

#### `getBlockedUsers(page?: number, size?: number): Promise<UserBlock[]>`

- [ ] Sends `GET /api/v1/users/me/blocked` with `page` (default `0`) and `size` (default `20`) as query params via Axios `params`.
- [ ] Unwraps with `.then(r => r.data.data)`.
- [ ] Returns `Promise<UserBlock[]>`.

---

### `adminApi.ts`

- [ ] Import `api` from `'./client'`.
- [ ] Import `Report`, `AdminUser`, `ReviewReportPayload`, `SuspendUserPayload`, `ReportStatus` from `'../types/moderation'`.

#### `getReports(status?: ReportStatus, page?: number, size?: number): Promise<Report[]>`

- [ ] Sends `GET /api/v1/admin/reports` with query params `status` (omitted when `undefined`), `page` (default `0`), `size` (default `20`).
- [ ] For the `status` param: when `undefined`, do not include it in the `params` object so it is not sent as `?status=undefined` — Axios omits keys whose values are `undefined`, but confirm this behaviour.
- [ ] Unwraps with `.then(r => r.data.data)`.

#### `reviewReport(id: string, payload: ReviewReportPayload): Promise<Report>`

- [ ] Sends `PUT /api/v1/admin/reports/{id}` with `payload` as the request body.
- [ ] Unwraps with `.then(r => r.data.data)`.
- [ ] Returns `Promise<Report>` (the updated report).

#### `suspendUser(id: string, payload: SuspendUserPayload): Promise<AdminUser>`

- [ ] Sends `PUT /api/v1/admin/users/{id}/suspend` with `payload` as the request body.
- [ ] Unwraps with `.then(r => r.data.data)`.

#### `unsuspendUser(id: string): Promise<AdminUser>`

- [ ] Sends `PUT /api/v1/admin/users/{id}/unsuspend` with no request body.
- [ ] Unwraps with `.then(r => r.data.data)`.

#### `getAdminUsers(filters?: { username?: string; status?: string }, page?: number, size?: number): Promise<AdminUser[]>`

- [ ] Sends `GET /api/v1/admin/users` with query params `username` (optional partial match), `status` (optional account status filter), `page` (default `0`), `size` (default `20`).
- [ ] Constructs the `params` object by spreading `filters` (omit `undefined` values) and adding `page` and `size`.
- [ ] Unwraps with `.then(r => r.data.data)`.

---

## Notes

- Both files must import only from `'./client'` and `'../types/moderation'`. Do not import from `'../types/user'` or other type files — if admin user data reuses fields from `UserProfileResponse`, duplicate only the needed fields in `AdminUser` (in `moderation.ts`) rather than importing across type domains.
- `blockUser` and `unblockUser` are intentionally simple one-line functions. Do not add error handling here — errors propagate to the React Query mutation and are handled there.
- The `getReports` endpoint's `status` filter is optional. When the admin dashboard shows "All Reports", it calls `getReports()` with no status. When showing "Pending Queue", it calls `getReports('PENDING')`. The function signature must clearly express this optionality.
- `adminApi.ts` is only ever called from components and hooks that are themselves behind the `AdminRoute` guard (TASK-9.17). However, do not rely on the guard as the sole security layer — the backend's `@PreAuthorize` and `hasRole("ADMIN")` in `SecurityConfig` remain the authoritative gates.
