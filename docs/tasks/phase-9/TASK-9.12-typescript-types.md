# TASK-9.12 — TypeScript Types: moderation.ts

## Overview

Create the TypeScript type definitions for the moderation and admin features. These types mirror the backend response DTOs from TASK-9.10 exactly so no manual field conversion is needed when Axios deserialises API responses.

---

## Requirements

- No `any` types. Strict TypeScript throughout.
- Use string literal union types for enums (not TypeScript `enum` keyword) to keep values directly comparable to API strings.
- All types exported individually — no default export.
- File must be self-contained — no imports from other type files unless strictly necessary.

---

## File Location

```
frontend/src/types/moderation.ts
```

---

## Checklist

### `ReportEntityType`

- [ ] String literal union type matching the backend `ReportEntityType` enum values sent in API responses: `'USER' | 'POST' | 'COMMENT' | 'MESSAGE'`.
- [ ] Note: the backend Java enum uses uppercase (`POST`, `USER`, etc.) and serialises to uppercase strings. The TypeScript type must match exactly.

### `ReportStatus`

- [ ] String literal union: `'PENDING' | 'REVIEWED' | 'RESOLVED' | 'DISMISSED'`.

### `ReportReason`

- [ ] String literal union of all valid report reason codes: `'SPAM' | 'HATE_SPEECH' | 'NUDITY' | 'VIOLENCE' | 'HARASSMENT' | 'FALSE_INFORMATION' | 'SELF_HARM' | 'OTHER'`.
- [ ] These values must match exactly what the backend `ReportRequest.reason` field accepts. If the backend uses a `ReportReason` enum, align these values with its members. If the backend accepts a free-form string, note that here but still use this union type on the frontend to constrain UI choices.

### `ReviewAction`

- [ ] String literal union: `'RESOLVE' | 'DISMISS' | 'MARK_REVIEWED'`.
- [ ] Used in the `ReviewReportRequest` payload sent from the admin panel.

### `AccountStatus`

- [ ] String literal union matching the PostgreSQL `account_status` ENUM values as returned in API responses: `'ACTIVE' | 'SUSPENDED' | 'DEACTIVATED' | 'PENDING_VERIFICATION'`.
- [ ] Reuse this type wherever `accountStatus` appears in user-related responses across the admin panel.

### `Report`

- [ ] Interface fields (mirrors `ReportResponse` from the backend):
  - `id: string`
  - `reporterId: string`
  - `reporterUsername: string`
  - `entityType: ReportEntityType`
  - `entityId: string`
  - `reason: string`
  - `details: string | null`
  - `status: ReportStatus`
  - `reviewedById: string | null`
  - `reviewedAt: string | null` — ISO 8601; parse with `new Date(...)` when displaying.
  - `createdAt: string` — ISO 8601.

### `UserBlock`

- [ ] Interface fields (mirrors `BlockedUserResponse` from the backend):
  - `blockedUserId: string`
  - `username: string`
  - `fullName: string | null`
  - `avatarUrl: string | null`
  - `blockedAt: string` — ISO 8601.

### `AdminUser`

- [ ] Interface fields (mirrors `AdminUserResponse` from the backend):
  - `id: string`
  - `username: string`
  - `email: string`
  - `fullName: string | null`
  - `accountStatus: AccountStatus`
  - `isVerified: boolean`
  - `createdAt: string`
  - `lastLoginAt: string | null`

### `SubmitReportPayload`

- [ ] Represents the request body sent to `POST /api/v1/reports`. Interface fields:
  - `entityType: ReportEntityType`
  - `entityId: string`
  - `reason: ReportReason`
  - `details?: string` — optional.

### `ReviewReportPayload`

- [ ] Represents the request body sent to `PUT /api/v1/admin/reports/{id}`. Interface fields:
  - `action: ReviewAction`

### `SuspendUserPayload`

- [ ] Represents the request body sent to `PUT /api/v1/admin/users/{id}/suspend`. Interface fields:
  - `reason: string`

### `AuditLog` (optional, for future admin audit log viewer)

- [ ] Interface fields (mirrors `audit_logs` schema):
  - `id: number` — BIGSERIAL maps to `number` in TypeScript.
  - `userId: string | null`
  - `action: string`
  - `entityType: string | null`
  - `entityId: string | null`
  - `metadata: string | null`
  - `ipAddress: string | null`
  - `createdAt: string`
- [ ] This type is not strictly required for Phase 9 but is included for completeness and to avoid re-typing it if an audit viewer is added in Phase 10.

---

## Notes

- All UUID fields are `string` — Axios deserialises them as strings from JSON.
- `ReportStatus` and `AccountStatus` are separate types even though they are both status enums. Do not merge them — their values are unrelated and each must align with its own backend enum.
- `UserBlock` in this file represents the API response shape (enriched with username and avatar from `BlockedUserResponse`), NOT the raw domain model. The raw domain model only has `blockerId`, `blockedId`, and `createdAt`. Using the enriched response shape is intentional — the frontend always displays blocked users with profile info.
- The `REPORT_REASON` display labels (shown in the `ReportDialog` UI) are a frontend concern, not a type concern. Define them as a separate `REPORT_REASON_LABELS: Record<ReportReason, string>` constant in the component that needs them, not in this types file.
