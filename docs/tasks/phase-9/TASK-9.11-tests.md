# TASK-9.11 — Unit & Integration Tests

## Overview

Write tests covering the moderation services, persistence adapters, and REST controllers. Follow the naming and structural conventions established in Phases 4, 7, and 8.

Unit tests verify business logic in isolation using Mockito. Integration tests for the persistence layer use `@DataJpaTest` against the real PostgreSQL schema. Controller integration tests use `@SpringBootTest` with `MockMvc` and `@MockBean` for all use-case dependencies.

---

## Requirements

- Unit tests: JUnit 5 + Mockito. No Spring context loaded.
- Persistence integration tests: `@DataJpaTest`. Use the project's existing test database configuration. If a dedicated `instagram_test` database is configured (as introduced in TASK-8.16), use the same approach.
- Controller integration tests: `@SpringBootTest` + `MockMvc`. Mock all use-case beans.
- Aim for ≥ 80% line coverage on all new production code.
- Test class naming: `<ClassUnderTest>Test` for unit tests, `<ClassUnderTest>IT` for integration tests.

---

## File Locations

```
backend/src/test/java/com/instagram/application/service/ModerationServiceTest.java
backend/src/test/java/com/instagram/application/service/AdminServiceTest.java
backend/src/test/java/com/instagram/adapter/out/persistence/ModerationPersistenceAdapterIT.java
backend/src/test/java/com/instagram/adapter/in/web/ModerationControllerIT.java
backend/src/test/java/com/instagram/adapter/in/web/AdminControllerIT.java
```

---

## Checklist

### `ModerationServiceTest.java` (unit)

#### Setup

- [ ] Mock all dependencies: `ModerationRepository`, `AuditLogRepository`, `UserRepository`.
- [ ] Create a `ModerationService` instance in `@BeforeEach` using the mocks (constructor injection).

#### `reportContent` tests

- [ ] **Happy path:** Call `reportContent` with a valid command. Verify `moderationRepository.saveReport(...)` is called once with a `Report` whose `status == PENDING`, `reporterId` matches the command, and `createdAt` is not null.
- [ ] **Audit log written:** After a successful report, verify `auditLogRepository.log(...)` is called once with action `"report_submit"`.
- [ ] **Duplicate report guard:** Stub `moderationRepository.existsByReporterIdAndEntityId(...)` to return `true`. Call `reportContent` again. Verify `saveReport` is NOT called a second time (idempotent behaviour).
- [ ] **Blank reason:** Pass a command with a blank `reason` string. Verify the service rejects it (either throws an exception or returns early) and `saveReport` is NOT called.

#### `blockUser` tests

- [ ] **Happy path:** Stub `userRepository.findByUsername(...)` to return a valid `User`. Stub `moderationRepository.isBlocked(...)` to return `false`. Call `blockUser`. Verify `saveBlock` is called and `auditLogRepository.log` is called with action `"user_block"`.
- [ ] **Already blocked:** Stub `isBlocked` to return `true`. Verify `AlreadyBlockedException` is thrown and `saveBlock` is NOT called.
- [ ] **Self-block:** Set `command.blockerId() == resolvedTargetId`. Verify `SelfBlockException` is thrown.
- [ ] **User not found:** Stub `userRepository.findByUsername(...)` to return empty. Verify `UserNotFoundException` is thrown.

#### `unblockUser` tests

- [ ] **Happy path:** Stub `isBlocked` to return `true`. Call `unblockUser`. Verify `deleteBlock` is called and audit log is written with `"user_unblock"`.
- [ ] **Not blocked:** Stub `isBlocked` to return `false`. Verify `NotBlockedException` is thrown and `deleteBlock` is NOT called.

#### `getBlockedUsers` tests

- [ ] Stub `moderationRepository.findBlocksByBlockerId(...)` to return a list of two `UserBlock` objects. Verify the service returns the same list and no audit log is written.

---

### `AdminServiceTest.java` (unit)

#### Setup

- [ ] Mock all dependencies: `ModerationRepository`, `UserRepository`, `AuditLogRepository`.
- [ ] Create an `AdminService` instance in `@BeforeEach`.
- [ ] Note: `@PreAuthorize` annotations on `AdminService` methods are not evaluated in unit tests (they require a Spring Security context). Test the business logic in isolation without worrying about role checks here. Role-based access control is covered in `AdminControllerIT`.

#### `reviewReport` tests

- [ ] **Resolve happy path:** Stub `findReportById` to return a `PENDING` report. Call `reviewReport` with action `RESOLVE`. Verify `saveReport` is called with a report whose `status == RESOLVED` and `reviewedById` is set. Verify audit log is written with `"report_resolve"`.
- [ ] **Dismiss happy path:** Same as above but with action `DISMISS`. Verify `status == DISMISSED` and audit log action `"report_dismiss"`.
- [ ] **Report not found:** Stub `findReportById` to return `Optional.empty()`. Verify `ReportNotFoundException` is thrown.
- [ ] **Double-resolve guard:** Stub `findReportById` to return an already `RESOLVED` report. Verify that calling `withResolved(...)` throws `IllegalStateException` (defined in the domain model's business method).

#### `suspendUser` tests

- [ ] **Happy path:** Stub `userRepository.findById(...)` to return an `ACTIVE` user. Call `suspendUser`. Verify the user's status is updated to `SUSPENDED` and audit log is written with `"user_suspend"` and the reason in metadata.
- [ ] **User not found:** Stub `findById` to return empty. Verify `UserNotFoundException` is thrown.
- [ ] **Already suspended:** Stub the user's status as `SUSPENDED`. Verify the service rejects the redundant call gracefully (documents whether it throws or is idempotent).

#### `unsuspendUser` tests

- [ ] **Happy path:** Stub a `SUSPENDED` user. Call `unsuspendUser`. Verify status updated to `ACTIVE` and audit log written.

---

### `ModerationPersistenceAdapterIT.java` (`@DataJpaTest`)

#### Setup

- [ ] Use `@DataJpaTest` to load only the JPA slice.
- [ ] Use `@BeforeEach` to insert a valid `users` row (required by FK constraints on `reports` and `user_blocks`). Insert at minimum two users: one as reporter/blocker, one as target.
- [ ] `@Autowired EntityManager` for flushing and clearing between operations.
- [ ] `@Autowired ReportJpaRepository`, `UserBlockJpaRepository`.

#### Report persistence

- [ ] **Save and retrieve:** Build a `Report` domain object with all required fields. Call `saveReport(report)`. Retrieve with `findReportById(saved.getId())`. Assert all fields round-trip correctly including `status = PENDING`.
- [ ] **Status update:** Call `findReportById`. Use `report.withResolved(adminId)`. Call `saveReport` on the updated report. Retrieve again. Assert `status == RESOLVED` and `reviewedById` is set.
- [ ] **Find pending reports:** Insert one PENDING and one RESOLVED report. Call `findPendingReports`. Verify only the PENDING report is returned.
- [ ] **Count by status:** Insert two PENDING and one RESOLVED report. Call `countByStatus(PENDING)`. Verify count is `2`.
- [ ] **Duplicate check:** Call `existsByReporterIdAndEntityId` before and after inserting a report. Verify it returns `false` then `true`.

#### Block persistence

- [ ] **Save and retrieve:** Save a `UserBlock`. Call `isBlocked(blockerId, blockedId)`. Verify `true` returned.
- [ ] **Direction is unidirectional:** Call `isBlocked(blockedId, blockerId)` (reversed). Verify `false` returned.
- [ ] **isEitherBlocked:** Call `isEitherBlocked(blockerId, blockedId)`. Verify `true`. Call `isEitherBlocked(blockedId, blockerId)`. Verify `true` (bidirectional check).
- [ ] **Delete block:** Save a block. Call `deleteBlock`. Call `isBlocked`. Verify `false` returned.
- [ ] **Find blocked IDs:** Insert two blocks from the same blocker. Call `findBlockedUserIdsByBlockerId`. Verify both IDs are in the returned list.
- [ ] **Find blocker IDs:** Insert a block targeting a specific user. Call `findBlockerIdsByBlockedId`. Verify the blocker ID is in the list.
- [ ] **Self-block guard in domain model:** Attempt to call `UserBlock.builder().blockerId(id).blockedId(id).build()`. Verify `IllegalArgumentException` is thrown (the domain model's self-block guard fires before anything reaches the DB).

---

### `ModerationControllerIT.java` (`@SpringBootTest` + `MockMvc`)

#### Setup

- [ ] `@MockBean` for: `ReportContentUseCase`, `BlockUserUseCase`, `UnblockUserUseCase`, `GetBlockedUsersUseCase`.
- [ ] Configure a test JWT with `ROLE_USER` authority for standard authenticated requests.

#### Tests

- [ ] `POST /api/v1/reports` — authenticated, valid body: returns `201 CREATED`.
- [ ] `POST /api/v1/reports` — missing required fields (`entityType` or `entityId` null): returns `400 BAD REQUEST`.
- [ ] `POST /api/v1/users/someuser/block` — authenticated: returns `200 OK`.
- [ ] `DELETE /api/v1/users/someuser/block` — authenticated: returns `204 NO CONTENT`.
- [ ] `GET /api/v1/users/me/blocked` — authenticated: returns `200 OK` with list.
- [ ] Any endpoint without valid JWT — returns `401 UNAUTHORIZED`.

---

### `AdminControllerIT.java` (`@SpringBootTest` + `MockMvc`)

#### Setup

- [ ] `@MockBean` for: `ReviewReportUseCase`, `SuspendUserUseCase`, `UnsuspendUserUseCase`, `AdminGetReportsUseCase`.
- [ ] Configure two test JWTs: one with `ROLE_ADMIN`, one with `ROLE_USER`.

#### Tests

- [ ] `GET /api/v1/admin/reports` — `ROLE_ADMIN` JWT: returns `200 OK`.
- [ ] `GET /api/v1/admin/reports?status=PENDING` — `ROLE_ADMIN` JWT: returns `200 OK` and `status` filter is passed to the use case.
- [ ] `PUT /api/v1/admin/reports/{id}` — `ROLE_ADMIN` JWT, body `{ "action": "RESOLVE" }`: returns `200 OK`.
- [ ] `PUT /api/v1/admin/reports/{id}` — `ROLE_ADMIN` JWT, missing `action` field: returns `400 BAD REQUEST`.
- [ ] `PUT /api/v1/admin/users/{id}/suspend` — `ROLE_ADMIN` JWT, body `{ "reason": "Spam" }`: returns `200 OK`.
- [ ] `PUT /api/v1/admin/users/{id}/unsuspend` — `ROLE_ADMIN` JWT: returns `200 OK`.
- [ ] `GET /api/v1/admin/reports` — `ROLE_USER` JWT (not admin): returns `403 FORBIDDEN`. This is the critical security test.
- [ ] Any admin endpoint without JWT — returns `401 UNAUTHORIZED`.

---

## Notes

- When stubbing use-case methods with Mockito in controller tests, return a valid domain object (e.g., a `Report` built with `Report.builder()` with all required fields set) so the controller's `ResponseEntity` mapping does not throw a `NullPointerException`.
- For `AdminControllerIT`, the test JWT for `ROLE_USER` must be a real signed JWT (using the same secret as the application's `JwtTokenProvider`) to pass the `JwtAuthenticationFilter`. Alternatively, use Spring Security's `@WithMockUser(roles = "USER")` annotation if the project's filter chain supports it — check how other controller integration tests configure test security.
- The `AuditLogPersistenceAdapterIT` is deliberately omitted — the audit log adapter's `log()` method wraps all exceptions silently, making it awkward to test at the IT level. A unit test verifying that exceptions are caught and logged (using Mockito's `verify`) is sufficient.
