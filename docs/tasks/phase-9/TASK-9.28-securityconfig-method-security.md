# TASK-9.28 — SecurityConfig & method-level authorization

## Overview

With authorities now loaded per request ([TASK-9.27](TASK-9.27-authorization-jwt-authorities.md)), turn on the two enforcement layers: **URL-level** rules in `SecurityConfig` for broad path families, and **method-level** `@PreAuthorize` checks on services for fine-grained, permission-based authorization. You also wire a JSON `AccessDeniedHandler` so denials return the project's `ApiResponse` envelope instead of Spring's default error page.

---

## File Locations

```
backend/src/main/java/com/instagram/infrastructure/security/SecurityConfig.java   ← modify
backend/src/main/java/com/instagram/domain/service/AdminService.java              ← modify (add @PreAuthorize)
backend/src/main/java/com/instagram/domain/service/ModerationService.java         ← modify (add @PreAuthorize where applicable)
backend/src/main/java/com/instagram/infrastructure/security/RestAccessDeniedHandler.java  ← create
```

---

## Checklist

### `SecurityConfig` (modify)

- [ ] Add `@EnableMethodSecurity` to the config class — activates `@PreAuthorize`.
- [ ] In `authorizeHttpRequests`, add **before** `anyRequest().authenticated()`:
  - [ ] `.requestMatchers("/api/v1/admin/roles/**").hasAuthority("ROLE_PERMISSION_MANAGE")` is **too coarse** — instead keep role/permission specificity at the method layer and use a broad guard here: `.requestMatchers("/api/v1/admin/**").hasAnyRole("MODERATOR", "ADMIN", "SUPER_ADMIN")`. This blocks plain users at the edge; the precise permission is enforced per endpoint by `@PreAuthorize`.
- [ ] Register the `RestAccessDeniedHandler` via `.exceptionHandling(e -> e.accessDeniedHandler(restAccessDeniedHandler))` so filter-chain 403s return JSON.
- [ ] Leave the existing `permitAll` rules (`/api/v1/auth/**`, `GET /api/v1/users/{username}`, swagger, oauth2, `/ws/**`) unchanged.

### `RestAccessDeniedHandler` (create)

- [ ] `implements AccessDeniedHandler`; write HTTP 403 with `Content-Type: application/json` and an `ApiResponse.error(...)` body. Keep the message generic ("Access denied") — don't leak which permission was missing.

### `@PreAuthorize` on services — endpoint → permission map

Annotate the use-case methods (or controller methods) so each maps to the right permission. The canonical map:

- [ ] `GET /api/v1/admin/reports` → `@PreAuthorize("hasAuthority('REPORT_VIEW')")`
- [ ] `PUT /api/v1/admin/reports/{id}` → `hasAuthority('REPORT_REVIEW')`
- [ ] `GET /api/v1/admin/users` → `hasAuthority('USER_VIEW')`
- [ ] `PUT /api/v1/admin/users/{id}/suspend` → `hasAuthority('USER_SUSPEND')`
- [ ] `PUT /api/v1/admin/users/{id}/unsuspend` → `hasAuthority('USER_UNSUSPEND')`
- [ ] `GET /api/v1/admin/audit-logs` (if exposed) → `hasAuthority('AUDIT_LOG_VIEW')`
- [ ] `GET /api/v1/admin/roles`, `GET /api/v1/admin/users/{id}/roles` → `hasAuthority('ROLE_VIEW')`
- [ ] `POST/DELETE /api/v1/admin/users/{id}/roles` → `hasAuthority('ROLE_ASSIGN')`
- [ ] `PUT /api/v1/admin/roles/{name}/permissions` → `hasAuthority('ROLE_PERMISSION_MANAGE')`

- [ ] Put the annotation in **one** place per action (prefer the service method so it's enforced regardless of caller) and document the choice. Do not double-annotate controller + service with conflicting expressions.

---

## Notes

- **Two layers, two jobs.** `SecurityConfig` path rules are a coarse early gate (cheap, blocks anonymous/plain users before controllers run). `@PreAuthorize` is the precise per-action check. The domain guards in [TASK-9.24](TASK-9.24-rbac-domain-service.md) are the third layer for rules an authority can't express (e.g. "admin can't grant super-admin").
- **`hasRole` vs `hasAuthority`.** `hasAnyRole("ADMIN")` matches authority `ROLE_ADMIN`; `hasAuthority("REPORT_REVIEW")` matches the bare permission. [TASK-9.27](TASK-9.27-authorization-jwt-authorities.md) grants both styles, so use roles for the broad path matcher and permissions for `@PreAuthorize`.
- **Test the negative path.** The valuable assertions are the *denials*: a `MODERATOR` calling suspend → 403; a plain `USER` hitting `/api/v1/admin/**` → 403 at the filter chain. Covered in [TASK-9.30](TASK-9.30-rbac-tests.md).

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Method security (`@PreAuthorize`, SpEL)** — fine-grained authorization on beans — https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html
- **Authorize HTTP requests (path matchers)** — the URL-level gate — https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html
- **Handling access-denied** — returning JSON instead of an error page — https://www.baeldung.com/spring-security-custom-access-denied-page

### Official docs (code reference)
- **Spring Security authorization** — https://docs.spring.io/spring-security/reference/servlet/authorization/index.html
- **`AccessDeniedHandler` (javadoc)** — https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/web/access/AccessDeniedHandler.html
