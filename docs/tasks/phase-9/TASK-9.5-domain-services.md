# TASK-9.5 — Domain Services: ModerationService, AdminService

## Overview

Create two service classes that implement all eight use-case in-ports from TASK-9.4. `ModerationService` handles user-facing moderation actions (reporting content, blocking/unblocking users, viewing the blocked list). `AdminService` handles admin-only actions (reviewing reports, suspending and unsuspending users).

Both services need Spring annotations (`@Service`, `@Transactional`). Because `AdminService` uses `@PreAuthorize`, it must live in `application/service/` — the same reasoning that placed `SearchService` and `NotificationService` there instead of `domain/service/`. Follow that same precedent for both services.

---

## Requirements

- Both live in `application/service/` — NOT `domain/service/`. Reason: Spring annotations (`@Service`, `@Transactional`, `@PreAuthorize`) cannot be used in the pure-Java domain layer.
- `@Service`, constructor injection only, all dependency fields `final`.
- Never put business logic in a controller. Every decision must be made here.
- Each service must write an audit log entry for every state-changing operation by calling `AuditLogRepository.log(...)`.

---

## File Locations

```
backend/src/main/java/com/instagram/application/service/ModerationService.java
backend/src/main/java/com/instagram/application/service/AdminService.java
```

---

## Checklist

### `ModerationService.java`

#### Dependencies to inject (constructor)

- [ ] `ModerationRepository` — for report and block persistence.
- [ ] `AuditLogRepository` — for writing audit entries after every mutation.
- [ ] `UserRepository` — for resolving a target username to a `UUID` in block/unblock operations.

#### Implements

- [ ] `ReportContentUseCase`
- [ ] `BlockUserUseCase`
- [ ] `UnblockUserUseCase`
- [ ] `GetBlockedUsersUseCase`

#### `reportContent` method

- [ ] Validate that `command.reason()` is not blank. Although Bean Validation on the request DTO catches this at the controller boundary, the service should also guard against blank reasons for defence in depth.
- [ ] Call `moderationRepository.existsByReporterIdAndEntityId(command.reporterId(), command.entityId())`. If `true`, throw a meaningful exception or return silently — decide the appropriate behaviour and document it here. The recommended behaviour is to return the existing report silently (idempotent) rather than throwing a conflict error, because double-tapping a "Report" button is a common accidental gesture.
- [ ] Build a new `Report` using `Report.builder()` — populate `reporterId`, `entityType`, `entityId`, `reason`, `details`, and `createdAt = OffsetDateTime.now()`. Leave `status` as the `PENDING` default.
- [ ] Call `moderationRepository.saveReport(report)` and capture the returned instance (which has the generated `id`).
- [ ] Call `auditLogRepository.log(...)` with action `"report_submit"`, `entityType = command.entityType().name()`, `entityId = command.entityId()`, metadata `null`, ipAddress `null`. Note: IP address is not available at the service layer — it would require passing it through from the controller, which is acceptable for future enhancement but out of scope now.
- [ ] Return the saved report.

#### `blockUser` method

- [ ] Annotate with `@Transactional`.
- [ ] Resolve `command.targetUsername()` to a `UUID` by calling `userRepository.findByUsername(command.targetUsername())`. If not found, throw `UserNotFoundException` (already exists from Phase 1).
- [ ] If `command.blockerId()` equals the resolved target `UUID`, throw `SelfBlockException`.
- [ ] Call `moderationRepository.isBlocked(command.blockerId(), resolvedTargetId)`. If already blocked, throw `AlreadyBlockedException`.
- [ ] Build a `UserBlock` via `UserBlock.builder()` — set `blockerId`, `blockedId`, `createdAt = OffsetDateTime.now()`.
- [ ] Call `moderationRepository.saveBlock(block)`.
- [ ] Call `auditLogRepository.log(...)` with action `"user_block"`, `entityType = "user"`, `entityId = resolvedTargetId`.
- [ ] Return the saved `UserBlock`.

#### `unblockUser` method

- [ ] Annotate with `@Transactional`.
- [ ] Resolve `command.targetUsername()` to a `UUID` via `userRepository.findByUsername(...)`. Throw `UserNotFoundException` if not found.
- [ ] Call `moderationRepository.isBlocked(command.blockerId(), resolvedTargetId)`. If NOT blocked, throw `NotBlockedException`.
- [ ] Call `moderationRepository.deleteBlock(command.blockerId(), resolvedTargetId)`.
- [ ] Call `auditLogRepository.log(...)` with action `"user_unblock"`, `entityType = "user"`, `entityId = resolvedTargetId`.

#### `getBlockedUsers` method

- [ ] Call `moderationRepository.findBlocksByBlockerId(command.userId(), PageRequest.of(query.page(), query.size()))`.
- [ ] Return the list as-is — no audit log needed for read operations.

---

### `AdminService.java`

#### Dependencies to inject (constructor)

- [ ] `ModerationRepository` — for finding and updating reports.
- [ ] `UserRepository` — for finding and updating users (suspend/unsuspend).
- [ ] `AuditLogRepository` — for writing audit entries.

#### Enable Method Security

- [ ] Confirm that `@EnableMethodSecurity` (Spring Security 6.x) or `@EnableGlobalMethodSecurity(prePostEnabled = true)` (older Spring Security) is present in `SecurityConfig.java` or another `@Configuration` class. If it is not already enabled, add it. Without this annotation, `@PreAuthorize` has no effect.
- [ ] Annotate every method in `AdminService` with `@PreAuthorize("hasRole('ADMIN')")`. This is the primary access control gate for all admin operations.

#### ROLE_ADMIN Setup Prerequisite

- [ ] Before writing `AdminService`, verify whether the `User` domain model and `UserJpaEntity` carry a `role` field. If they do not, a Flyway migration must be created to add a `role VARCHAR(50) NOT NULL DEFAULT 'USER'` column to the `users` table, and the `User` domain model, `UserJpaEntity`, `UserPersistenceAdapter`, and JWT token generation must be updated to include the role. Document this dependency here as a blocker — `AdminService` cannot be fully tested without the role claim in the JWT.
- [ ] Define a `UserRole` enum (or reuse an existing one) with at least `USER` and `ADMIN` values. Place it in `domain/model/` if it does not already exist.

#### Implements

- [ ] `ReviewReportUseCase`
- [ ] `SuspendUserUseCase`
- [ ] `UnsuspendUserUseCase`
- [ ] `AdminGetReportsUseCase`

#### `reviewReport` method

- [ ] Annotate with `@PreAuthorize("hasRole('ADMIN')")`.
- [ ] Annotate with `@Transactional`.
- [ ] Call `moderationRepository.findReportById(command.reportId())`. If empty, throw `ReportNotFoundException`.
- [ ] Based on `command.action()`:
  - `RESOLVE` → call `report.withResolved(command.adminId())` to obtain the updated report.
  - `DISMISS` → call `report.withDismissed(command.adminId())`.
  - `MARK_REVIEWED` → call `report.withReviewed(command.adminId())`.
- [ ] Call `moderationRepository.saveReport(updatedReport)` to persist the status change.
- [ ] Call `auditLogRepository.log(...)` with action matching the review action (e.g., `"report_resolve"`, `"report_dismiss"`, `"report_reviewed"`), `entityType = "report"`, `entityId = command.reportId()`.
- [ ] Return the updated report.

#### `suspendUser` method

- [ ] Annotate with `@PreAuthorize("hasRole('ADMIN')")`.
- [ ] Annotate with `@Transactional`.
- [ ] Call `userRepository.findById(command.targetUserId())`. Throw `UserNotFoundException` if absent.
- [ ] Verify the target user is not already suspended. If `user.getStatus() == SUSPENDED`, either throw `IllegalStateException` or return the user unchanged — document the decision.
- [ ] Update the user's `account_status` to `SUSPENDED`. The `User` domain model should have a `withSuspended()` method (or equivalent) — if it does not, add it as part of this task (it belongs on the domain model, but the `User.java` file is in Phase 1 scope). Alternatively, call `userRepository.updateStatus(id, SUSPENDED)` if a direct persistence update is more practical for the current implementation.
- [ ] Call `auditLogRepository.log(...)` with action `"user_suspend"`, `entityType = "user"`, `entityId = command.targetUserId()`, metadata containing the suspension reason as a simple JSON string.
- [ ] Return the updated `User`.

#### `unsuspendUser` method

- [ ] Annotate with `@PreAuthorize("hasRole('ADMIN')")`.
- [ ] Annotate with `@Transactional`.
- [ ] Find the user. Throw `UserNotFoundException` if absent.
- [ ] Update `account_status` to `ACTIVE`.
- [ ] Call `auditLogRepository.log(...)` with action `"user_unsuspend"`.
- [ ] Return the updated `User`.

#### `getReports` method

- [ ] Annotate with `@PreAuthorize("hasRole('ADMIN')")`.
- [ ] Call `moderationRepository.findAllReports(query.status(), PageRequest.of(query.page(), query.size()))`.
- [ ] Return the list. No audit log for read operations.

---

## Notes

- `@PreAuthorize` is the first line of defence. `UnauthorizedModerationAccessException` is a secondary guard for any path where method security is bypassed (e.g., direct service-layer calls in tests without a security context). Use both.
- The `suspendUser` implementation depends on whether `User.java` supports mutation. If `User` is fully immutable and uses copy-and-return methods (`withSuspended()`), ensure that pattern is added to `User.java` alongside this task. If `User` uses direct setters internally (which `Post.java` does via its `withUpdate()` copy pattern), follow that same approach.
- Do not send notification emails directly from `AdminService` — publish a domain event instead (or, for simplicity in Phase 9, leave notifications as a TODO and document it in the task's Notes. Adding an email/notification on suspend can be wired in Phase 10.)
