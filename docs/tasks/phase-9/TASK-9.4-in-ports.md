# TASK-9.4 — In-Ports: Use-Case Interfaces

## Overview

Create eight use-case interfaces (in-ports) for the moderation and admin feature. There is one interface per use case, one method per interface, and one inner `Command` or `Query` record per interface. This follows the exact same structure as the notification in-ports (TASK-7.5) and search in-ports (TASK-8.3).

User-facing use cases live in a `moderation` sub-package; admin-only use cases live in an `admin` sub-package.

---

## Requirements

- Pure Java — no Spring, JPA, or Lombok imports whatsoever.
- One file per use case.
- Interfaces live in `domain/port/in/moderation/` and `domain/port/in/admin/`.
- Return types use only domain model types (`Report`, `UserBlock`, `User`).

---

## File Locations

```
backend/src/main/java/com/instagram/domain/port/in/moderation/ReportContentUseCase.java
backend/src/main/java/com/instagram/domain/port/in/moderation/BlockUserUseCase.java
backend/src/main/java/com/instagram/domain/port/in/moderation/UnblockUserUseCase.java
backend/src/main/java/com/instagram/domain/port/in/moderation/GetBlockedUsersUseCase.java

backend/src/main/java/com/instagram/domain/port/in/admin/ReviewReportUseCase.java
backend/src/main/java/com/instagram/domain/port/in/admin/SuspendUserUseCase.java
backend/src/main/java/com/instagram/domain/port/in/admin/UnsuspendUserUseCase.java
backend/src/main/java/com/instagram/domain/port/in/admin/AdminGetReportsUseCase.java
```

---

## Checklist

### User-Facing In-Ports

#### `ReportContentUseCase.java`

- [ ] Method: `Report reportContent(Command command)`
- [ ] Inner record `Command` with the following fields:
  - `UUID reporterId` — the authenticated user submitting the report.
  - `ReportEntityType entityType` — the type of the reported content (`POST`, `COMMENT`, `USER`, or `MESSAGE`).
  - `UUID entityId` — the UUID of the content being reported.
  - `String reason` — mandatory short reason label (e.g., `"SPAM"`, `"HATE_SPEECH"`, `"NUDITY"`, `"VIOLENCE"`). The allowed values are enforced by Bean Validation on the request DTO (TASK-9.10), not in this record.
  - `String details` — optional free-text elaboration; may be `null` or blank.
- [ ] The method returns the newly created `Report` domain object (with its generated `id` populated by the persistence adapter).

#### `BlockUserUseCase.java`

- [ ] Method: `UserBlock blockUser(Command command)`
- [ ] Inner record `Command` with the following fields:
  - `UUID blockerId` — the authenticated user performing the block.
  - `String targetUsername` — the username of the user to be blocked. The service resolves this to a `UUID` via `UserRepository.findByUsername()` before creating the block.
- [ ] The method returns the newly created `UserBlock`.

#### `UnblockUserUseCase.java`

- [ ] Method: `void unblockUser(Command command)`
- [ ] Inner record `Command` with the following fields:
  - `UUID blockerId` — the authenticated user performing the unblock.
  - `String targetUsername` — the username of the user to be unblocked.
- [ ] The method returns `void`. There is no meaningful return value after deletion.

#### `GetBlockedUsersUseCase.java`

- [ ] Method: `List<UserBlock> getBlockedUsers(Query query)`
- [ ] Inner record `Query` with the following fields:
  - `UUID userId` — the authenticated user whose blocked list is being retrieved.
  - `int page` — zero-based page number.
  - `int size` — page size (default 20 in the controller).
- [ ] The method returns a paginated list of `UserBlock` records. The controller (TASK-9.9) enriches each `UserBlock` with user profile info by calling `UserRepository` to look up the blocked user's username and avatar.

---

### Admin In-Ports

#### `ReviewReportUseCase.java`

- [ ] Method: `Report reviewReport(Command command)`
- [ ] Inner record `Command` with the following fields:
  - `UUID adminId` — the authenticated admin performing the review action.
  - `UUID reportId` — the ID of the report to review.
  - `ReviewAction action` — an inner enum with values `RESOLVE`, `DISMISS`, and `MARK_REVIEWED`. Define this as a nested enum inside the `Command` record, or as a top-level enum in `domain/model/`. Use whichever is more readable but remain consistent with how `NotificationType` is declared in the project.
- [ ] The method returns the updated `Report` domain object reflecting the new status.

#### `SuspendUserUseCase.java`

- [ ] Method: `User suspendUser(Command command)`
- [ ] Inner record `Command` with the following fields:
  - `UUID adminId` — the admin performing the action; written to the audit log.
  - `UUID targetUserId` — the user being suspended.
  - `String reason` — mandatory reason for the suspension; written to the audit log and may be communicated to the suspended user via notification.
- [ ] The method returns the updated `User` domain object with `status = SUSPENDED`.
- [ ] Note: `account_status` is a PostgreSQL ENUM with values `active`, `suspended`, `deactivated`, `pending_verification`. The `User` domain model already has a `UserStatus` (or `AccountStatus`) field that maps to this column. Verify the field name and type in `User.java` before referencing it.

#### `UnsuspendUserUseCase.java`

- [ ] Method: `User unsuspendUser(Command command)`
- [ ] Inner record `Command` with the following fields:
  - `UUID adminId` — the admin performing the action.
  - `UUID targetUserId` — the user being reinstated.
- [ ] The method returns the updated `User` domain object with `status = ACTIVE`.

#### `AdminGetReportsUseCase.java`

- [ ] Method: `List<Report> getReports(Query query)`
- [ ] Inner record `Query` with the following fields:
  - `ReportStatus status` — filter by status; may be `null` to return all reports.
  - `int page` — zero-based page number.
  - `int size` — page size.
- [ ] The method returns a filtered, paginated list of reports ordered by `created_at DESC`.

---

## Notes

- All eight use cases are implemented by two service classes: `ModerationService` (user-facing, TASK-9.5) and `AdminService` (admin-only, TASK-9.5).
- The `moderation` and `admin` sub-packages mirror the `search` sub-package pattern introduced in Phase 8. Keep the sub-package structure so domain concepts are not mixed with auth, feed, or other domains in `port/in/`.
- `GetBlockedUsersUseCase` returns `List<UserBlock>` rather than `List<User>` because the `UserBlock` record carries the `createdAt` timestamp which the UI displays alongside each blocked user (e.g., "Blocked 2 months ago").
- `ReportContentUseCase.Command.reason` is a plain `String` at the domain level. If you want to express reason as a typed enum (e.g., `ReportReason.SPAM`), define `ReportReason` in `domain/model/` and use it here instead of `String`. Using an enum is strongly preferred to prevent invalid reasons from reaching the database — align with the TypeScript `ReportReason` type in TASK-9.12.
