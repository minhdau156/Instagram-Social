# TASK-9.2 — Domain Exceptions

## Overview

Create all domain-specific exceptions for the moderation and admin features, and register their HTTP mappings in `GlobalExceptionHandler`. Following the project convention established in Phases 4 and 7, each exception is a thin unchecked wrapper that communicates a specific business rule violation. The `GlobalExceptionHandler` maps each exception to an appropriate HTTP status code.

---

## Requirements

- All exception classes live in `domain/exception/` — no Spring, JPA, or Lombok imports.
- Every exception must extend `RuntimeException`.
- Each exception requires a constructor that accepts a `String message` and passes it to `super(message)`.
- HTTP mappings go in `GlobalExceptionHandler.java` in `adapter/in/web/` — never in the exception class itself.
- Use the existing exception classes (`PostNotFoundException`, `AlreadyLikedException`, etc.) as style references.

---

## File Locations

```
backend/src/main/java/com/instagram/domain/exception/ReportNotFoundException.java
backend/src/main/java/com/instagram/domain/exception/AlreadyBlockedException.java
backend/src/main/java/com/instagram/domain/exception/NotBlockedException.java
backend/src/main/java/com/instagram/domain/exception/SelfBlockException.java
backend/src/main/java/com/instagram/domain/exception/UnauthorizedModerationAccessException.java

backend/src/main/java/com/instagram/adapter/in/web/GlobalExceptionHandler.java  ← modify
```

---

## Checklist

### `ReportNotFoundException.java`

- [x] Extends `RuntimeException`.
- [x] Constructor accepts a `UUID reportId` parameter. The message should clearly state which report ID was not found, e.g. `"Report not found: " + reportId`.
- [x] **When thrown:** In `ModerationService` and `AdminService` when `findReportById` returns an empty result.
- [x] **HTTP mapping:** `404 NOT FOUND`.

---

### `AlreadyBlockedException.java`

- [x] Extends `RuntimeException`.
- [x] Constructor accepts `UUID blockerId` and `UUID blockedId`. Message should indicate that the block relationship already exists, e.g. `"User " + blockerId + " has already blocked user " + blockedId`.
- [x] **When thrown:** In `ModerationService.blockUser()` before attempting to insert a duplicate block. Check `ModerationRepository.isBlocked()` first; if it returns true, throw this exception rather than letting a database unique-constraint violation bubble up.
- [x] **HTTP mapping:** `409 CONFLICT`.

---

### `NotBlockedException.java`

- [x] Extends `RuntimeException`.
- [x] Constructor accepts `UUID blockerId` and `UUID blockedId`. Message should indicate that no block relationship exists to remove, e.g. `"User " + blockerId + " has not blocked user " + blockedId`.
- [x] **When thrown:** In `ModerationService.unblockUser()` when the caller tries to unblock a user they have not blocked.
- [x] **HTTP mapping:** `404 NOT FOUND`.

---

### `SelfBlockException.java`

- [x] Extends `RuntimeException`.
- [x] Constructor accepts a `UUID userId` parameter. Message should be `"User " + userId + " cannot block themselves"`.
- [x] **When thrown:** In `ModerationService.blockUser()` before the repository call, when `blockerId == blockedId`. This is a secondary guard — the domain model's `UserBlock.Builder.build()` also enforces this rule, but the service should throw this named exception (not let an `IllegalArgumentException` propagate from the builder) so the `GlobalExceptionHandler` can map it cleanly.
- [x] **HTTP mapping:** `400 BAD REQUEST`.

---

### `UnauthorizedModerationAccessException.java`

- [x] Extends `RuntimeException`.
- [x] Constructor accepts a `UUID userId` and a `String action`. Message should indicate the user lacks permission to perform the action, e.g. `"User " + userId + " is not authorized to perform: " + action`.
- [x] **When thrown:** In `AdminService` when the caller's JWT does not carry the `ROLE_ADMIN` authority, as a secondary guard for any admin-only use case that is also callable from a path without `@PreAuthorize`.
- [x] **HTTP mapping:** `403 FORBIDDEN`.

---

### `GlobalExceptionHandler.java` — Additions

- [x] Open `GlobalExceptionHandler.java`. Do not remove any existing handler methods.
- [x] Add a handler method for `ReportNotFoundException` returning `ResponseEntity` with status `404 NOT FOUND` and the exception message as the error body.
- [x] Add a handler method for `AlreadyBlockedException` returning `ResponseEntity` with status `409 CONFLICT`.
- [x] Add a handler method for `NotBlockedException` returning `ResponseEntity` with status `404 NOT FOUND`.
- [x] Add a handler method for `SelfBlockException` returning `ResponseEntity` with status `400 BAD REQUEST`.
- [x] Add a handler method for `UnauthorizedModerationAccessException` returning `ResponseEntity` with status `403 FORBIDDEN`.
- [x] All new handler methods must follow the existing response format used throughout the project — wrap the error in the same `ApiResponse` envelope already used by the existing handlers. Check the existing methods in `GlobalExceptionHandler.java` before writing to ensure consistency.

---

## Notes

- The `SelfBlockException` may seem redundant with the domain model's `IllegalArgumentException`, but having a named exception allows the `GlobalExceptionHandler` to produce a clean `400` response with a user-friendly message rather than a generic 500.
- Do not add `@ResponseStatus` annotations to the exception classes. HTTP status mapping is centralised in `GlobalExceptionHandler` — mixing it into the exceptions themselves creates two sources of truth.
- `UnauthorizedModerationAccessException` is distinct from Spring Security's `AccessDeniedException`. Spring Security's exception already returns 403 via its own handler. `UnauthorizedModerationAccessException` is for the cases where the service layer performs its own role check programmatically (e.g., checking `user.getRole() == ADMIN` before acting).
