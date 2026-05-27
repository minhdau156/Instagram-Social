# TASK-9.30 — Tests (RBAC & authorization)

## Overview

Prove the authorization rules hold — especially the **negative** paths, which are the whole point of an access-control system. Cover the domain guards (unit), the persistence mapping (integration), the management API (MockMvc with different roles), and the end-to-end authorization wiring (a moderator is allowed to review reports but forbidden from suspending users).

---

## File Locations

```
backend/src/test/java/com/instagram/domain/service/RbacServiceTest.java                      ← create
backend/src/test/java/com/instagram/adapter/out/persistence/RbacPersistenceAdapterIT.java    ← create
backend/src/test/java/com/instagram/adapter/in/web/RoleAdminControllerTest.java              ← create
backend/src/test/java/com/instagram/infrastructure/security/AuthorizationIT.java             ← create
```

---

## Checklist

### `RbacServiceTest` (JUnit 5 + Mockito)

- [x] assign role: happy path persists + writes audit log (verify `AuditLogRepository.log` with `ArgumentCaptor`).
- [x] assign `ADMIN` by a non-super-admin actor → `InsufficientPrivilegeException`.
- [x] assign a role the user already holds → `RoleAlreadyAssignedException`.
- [x] revoke `SUPER_ADMIN` when `countUsersWithRole(SUPER_ADMIN) == 1` → `InsufficientPrivilegeException` (last-super-admin guard).
- [x] `updateRolePermissions` removing `ROLE_PERMISSION_MANAGE` from `SUPER_ADMIN` → `ProtectedRoleException`.
- [x] `updateRolePermissions` by non-super-admin → `InsufficientPrivilegeException`.
- [x] `assignDefaultRole` grants `USER` and is idempotent (no duplicate when already held).
- [x] `getUserPermissions` for a user with no roles → empty set, never null.

### `RbacPersistenceAdapterIT` (`@DataJpaTest`)

- [x] Seed roles/permissions/join rows (via `@Sql` or builder inserts), then `findRolesByUserId` returns roles with permissions populated.
- [x] `findPermissionNamesByUserId` returns the flattened, de-duplicated set (assert no N+1 if you can — count queries).
- [x] `assignRoleToUser` then `userHasRole` → true; `revokeRoleFromUser` → false.
- [x] `replaceRolePermissions` overwrites the join rows (old removed, new present).
- [x] `countUsersWithRole` reflects assignments.

### `RoleAdminControllerTest` (`@WebMvcTest` + MockMvc, use-case `@MockBean`s)

- [x] `GET /admin/roles` with a principal holding `ROLE_VIEW` → 200; without it → 403.
- [x] `PUT /admin/roles/{name}/permissions` with `ROLE_PERMISSION_MANAGE` → 200; with only `ROLE_ASSIGN` → 403.
- [x] `POST /admin/users/{id}/roles` with `ROLE_ASSIGN` → 200/201; unauthenticated → 401.
- [x] Use `@WithMockUser(authorities = {...})` (or a JWT post-processor) to set the exact authorities per case.

### `AuthorizationIT` (`@SpringBootTest`, full filter chain)

- [x] A real token for a `MODERATOR` can `PUT /admin/reports/{id}` (200) but is **forbidden** `PUT /admin/users/{id}/suspend` (403).
- [x] A plain `USER` hitting any `/api/v1/admin/**` → 403 (denied at the filter chain, returns the JSON `ApiResponse` from the `AccessDeniedHandler`).
- [x] An `ADMIN` assigning `SUPER_ADMIN` → 403 (privilege-escalation guard surfaces as the mapped status).
- [x] No `Authorization` header on an admin route → 401.

---

## Notes

- **Negative tests are the deliverable.** A green "admin can do admin things" test proves little; the assertions that *moderator cannot suspend* and *plain user cannot reach /admin* are what protect the system. Prioritize them.
- **Match existing test conventions.** `*Test` for unit/MockMvc, `*IT` for integration; mirror `ModerationControllerTest` / `ModerationPersistenceAdapterIT` from [TASK-9.11](TASK-9.11-tests.md).
- **Authorities, not just roles.** When mocking the principal, grant the bare permission authorities (`REPORT_REVIEW`) the same way [TASK-9.27](TASK-9.27-authorization-jwt-authorities.md) does — otherwise `@PreAuthorize("hasAuthority('REPORT_REVIEW')")` won't match.

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Testing Spring Security** — `@WithMockUser`, authorities, request post-processors — https://docs.spring.io/spring-security/reference/servlet/test/index.html
- **Why test the deny path** — negative authorization testing — https://owasp.org/www-project-web-security-testing-guide/

### Official docs (code reference)
- **Spring Security testing** — https://docs.spring.io/spring-security/reference/servlet/test/method.html
- **`@DataJpaTest` / MockMvc** — https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html
