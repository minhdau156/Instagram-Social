# TASK-9.27 — Authorization wiring: JWT authorities & authentication filter

## Overview

This is the task that **makes authorization actually work**. Right now the system is half-wired:

- `JwtTokenProvider.generateAccessToken(UUID, String role)` writes a `"role"` claim into the token, **but** `validateAccessToken` only reads the `subject` (userId) and ignores the claim.
- `JwtAuthenticationFilter` builds its `UserDetails` with `authorities(Collections.emptyList())` — so **every authenticated user has zero authorities**, and `hasRole('ADMIN')` / `hasAuthority(...)` can never pass.

You will close that gap by loading the authenticated user's roles **and** permissions and putting them into the `Authentication` as `GrantedAuthority`s, using Spring's conventions: roles as `ROLE_<NAME>` and permissions as the bare permission name.

---

## Design decision — where do authorities come from?

| Approach | Pros | Cons |
|---|---|---|
| **A. DB-backed (recommended)** — the filter resolves `userId` → `GetUserPermissionsUseCase` → authorities, per request | Runtime permission/role changes take effect immediately (no re-login); tokens stay small | One indexed query per request (cacheable in Redis, [TASK-10.3](../phase-10/TASK-10.3-redis-caching.md)) |
| **B. Embed in JWT** — put roles/permissions in claims, read them in the filter | Zero DB hit | Authorities are **stale** until the token expires; a revoked admin keeps access until refresh — bad for an admin/RBAC surface |

Because this project chose **runtime role & permission management**, use **Approach A**. Keep the `"role"` claim for the frontend's convenience if you like, but authorities are authoritative from the DB.

---

## File Locations

```
backend/src/main/java/com/instagram/infrastructure/security/JwtAuthenticationFilter.java   ← modify
backend/src/main/java/com/instagram/infrastructure/security/JwtTokenProvider.java          ← modify (optional)
```

---

## Checklist

### `JwtAuthenticationFilter` (modify)

- [x] Constructor-inject `GetUserPermissionsUseCase` and `RoleRepository` (or a single `GetUserRolesUseCase`) alongside the existing `JwtTokenProvider`.
- [x] After `validateAccessToken` yields the `userId`, load:
  - the user's roles → map each to `new SimpleGrantedAuthority("ROLE_" + roleName.name())`
  - the user's permissions → map each to `new SimpleGrantedAuthority(permissionName.name())`
- [x] Build the `UserDetails` (or principal) with the **combined** authority list — replace `Collections.emptyList()`.
- [x] Set the resulting `UsernamePasswordAuthenticationToken(principal, null, authorities)` on the `SecurityContextHolder`.
- [x] Keep the principal name = `userId.toString()` so the existing `currentUserId()` helper in controllers keeps working unchanged.
- [x] If authority loading fails (user deleted, etc.), leave the context unauthenticated and continue the chain — never throw out of the filter.

### `JwtTokenProvider` (optional)

- [x] No change is required for Approach A. If you want the frontend to read roles straight from the token, change the claim from a single `role` String to a `roles` list and populate it at login — but treat it as **display only**, never as the authorization source.

### Performance

- [x] The per-request lookup must be a single indexed query (`findPermissionNamesByUserId`, backed by `idx_user_roles_user`). Note in a comment that this is the place to add a short-TTL Redis cache keyed by `userId` once [TASK-10.3](../phase-10/TASK-10.3-redis-caching.md) lands.

---

## Notes

- **`ROLE_` prefix is a Spring convention, not cosmetic.** `hasRole('ADMIN')` checks for an authority literally named `ROLE_ADMIN`. `hasAuthority('REPORT_REVIEW')` checks for `REPORT_REVIEW` (no prefix). Granting both styles lets `SecurityConfig` use role checks for broad path families and `@PreAuthorize` use permission checks for fine-grained actions.
- **This unblocks Phase 9's existing admin tasks.** [TASK-9.9](TASK-9.9-rest-controllers.md) already assumes `hasRole("ADMIN")` works and even flags this exact prerequisite ("verify the JWT includes the role… convert it into a `GrantedAuthority`"). This task is that prerequisite, done properly with permissions.
- **Don't reintroduce a session.** The chain is stateless (`SessionCreationPolicy.STATELESS`); authorities are rebuilt per request from the token's `userId` + the DB. That is correct and intended.

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Authentication vs authorization** — who you are vs what you may do — https://docs.spring.io/spring-security/reference/servlet/authorization/index.html
- **`GrantedAuthority` & the `ROLE_` prefix** — how `hasRole`/`hasAuthority` resolve — https://docs.spring.io/spring-security/reference/servlet/authorization/architecture.html
- **Stateless JWT auth filters** — building the `Authentication` per request — https://www.baeldung.com/spring-security-oauth-jwt
- **Token staleness vs DB lookups** — why revocation matters for admin surfaces — https://auth0.com/docs/secure/tokens/token-best-practices

### Official docs (code reference)
- **Spring Security architecture (filter chain)** — https://docs.spring.io/spring-security/reference/servlet/architecture.html
- **`SimpleGrantedAuthority` (javadoc)** — https://docs.spring.io/spring-security/site/docs/current/api/org/springframework/security/core/authority/SimpleGrantedAuthority.html
