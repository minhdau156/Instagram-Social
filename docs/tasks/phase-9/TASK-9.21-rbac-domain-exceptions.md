# TASK-9.21 — Domain exceptions (RBAC)

## Overview

Add the named domain exceptions for the authorization subsystem and wire them into the existing `GlobalExceptionHandler`. Consistent with the project rule, the domain never throws raw `RuntimeException` — every failure mode gets a named exception in `domain/exception/`, and the adapter layer maps it to an HTTP status using the `ApiResponse.error()` envelope.

---

## File Locations

```
backend/src/main/java/com/instagram/domain/exception/RoleNotFoundException.java            ← create
backend/src/main/java/com/instagram/domain/exception/RoleAlreadyAssignedException.java      ← create
backend/src/main/java/com/instagram/domain/exception/RoleNotAssignedException.java          ← create
backend/src/main/java/com/instagram/domain/exception/InsufficientPrivilegeException.java    ← create
backend/src/main/java/com/instagram/domain/exception/ProtectedRoleException.java            ← create
backend/src/main/java/com/instagram/adapter/in/web/GlobalExceptionHandler.java              ← modify
```

---

## Checklist

### Exceptions (pure Java, no framework annotations, no `@ResponseStatus`)

- [x] `RoleNotFoundException` — a role name/id was not found in the DB. → **404**
- [x] `RoleAlreadyAssignedException` — the user already holds the role being assigned. → **409**
- [x] `RoleNotAssignedException` — attempted to revoke a role the user does not hold. → **404** (or 409 — pick one and stay consistent with the block/unblock choice in [TASK-9.2](TASK-9.2-domain-exceptions.md)).
- [x] `InsufficientPrivilegeException` — the caller may not perform this assignment (e.g. an `ADMIN` trying to grant `SUPER_ADMIN`, or removing the last super-admin). → **403**
- [x] `ProtectedRoleException` — attempt to delete/rename a `is_system` role, or to strip `ROLE_PERMISSION_MANAGE` from `SUPER_ADMIN`. → **409**
- [x] Each carries a clear message including the offending role/user identifier.

### `GlobalExceptionHandler` (modify)

- [x] Add one `@ExceptionHandler` method per exception above, returning `ApiResponse.error(...)` with the mapped status.
- [x] Use `log.warn(...)` (not `error`) — these are expected client/authorization failures, mirroring the existing moderation handlers from [TASK-9.2](TASK-9.2-domain-exceptions.md).
- [x] Add a handler for Spring Security's `AuthorizationDeniedException` / `AccessDeniedException` → **403** with the `ApiResponse` envelope, so `@PreAuthorize` denials ([TASK-9.28](TASK-9.28-securityconfig-method-security.md)) return a consistent JSON body instead of the default Spring error page. (Note: filter-chain denials are handled separately — see Notes.)

---

## Notes

- **Two layers throw 403 for different reasons.** `InsufficientPrivilegeException` is a *domain* decision (this caller can't make this specific assignment). `AccessDeniedException` is a *Spring Security* decision (this principal lacks the required authority). Both map to 403, but keep them distinct so error messages stay meaningful.
- **Filter-chain 403 vs method 403.** A request rejected by `requestMatchers(...).hasAuthority(...)` in `SecurityConfig` is denied *before* any controller runs, so `@RestControllerAdvice` cannot catch it — that path is handled by the security `AccessDeniedHandler`. `@RestControllerAdvice` only catches denials thrown from inside a method (`@PreAuthorize` on a service). [TASK-9.28](TASK-9.28-securityconfig-method-security.md) covers wiring a JSON `AccessDeniedHandler` for the filter-chain case.
- **Don't add a generic `ForbiddenException`.** Prefer the specific names above so the audit log and API responses explain *why* access was denied.

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **401 vs 403** — unauthenticated vs authenticated-but-not-allowed — https://developer.mozilla.org/en-US/docs/Web/HTTP/Status/403
- **Exception-to-HTTP mapping in Spring** — `@RestControllerAdvice` / `@ExceptionHandler` — https://www.baeldung.com/exception-handling-for-rest-with-spring

### Official docs (code reference)
- **Spring `@ControllerAdvice`** — https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-advice.html
- **Spring Security `AccessDeniedException`** — https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/access/AccessDeniedException.html
