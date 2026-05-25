# TASK-9.24 — Domain service: RbacService

## Overview

Implement all RBAC in-ports in a single `RbacService`. This is where the **authorization business rules** live — the rules that Spring Security's coarse `hasAuthority(...)` checks cannot express: who may grant which role, protecting the last super-admin, protecting system roles, and writing an audit-log entry for every change. It delegates persistence to `RoleRepository` / `PermissionRepository` ([TASK-9.22](TASK-9.22-rbac-out-ports.md)) and reuses the `AuditLogRepository` from [TASK-9.3](TASK-9.3-out-ports.md).

---

## File Locations

```
backend/src/main/java/com/instagram/domain/service/RbacService.java   ← create
```

---

## Checklist

### Wiring

- [ ] `@Service`; constructor-inject `RoleRepository`, `PermissionRepository`, `AuditLogRepository` — all `final`.
- [ ] Implements `AssignRoleToUserUseCase`, `RevokeRoleFromUserUseCase`, `GetUserRolesUseCase`, `GetUserPermissionsUseCase`, `ListRolesUseCase`, `UpdateRolePermissionsUseCase`, `AssignDefaultRoleUseCase`.

### `assignRoleToUser`

- [ ] Resolve the target `Role` by name → `RoleNotFoundException` if absent.
- [ ] **Privilege-escalation guard:** load the actor's roles. Only a `SUPER_ADMIN` may grant `ADMIN` or `SUPER_ADMIN`. An `ADMIN` may grant only `USER` / `MODERATOR`. Otherwise → `InsufficientPrivilegeException`.
- [ ] If the user already holds the role → `RoleAlreadyAssignedException`.
- [ ] Persist via `roleRepository.assignRoleToUser(targetUserId, roleId, actorId)`.
- [ ] Audit: `auditLogRepository.log(actorId, "ROLE_ASSIGNED", "USER", targetUserId, "role=" + roleName)`.
- [ ] `@Transactional`.

### `revokeRoleFromUser`

- [ ] Same privilege guard as assignment (you may only revoke roles you could grant).
- [ ] If the user does not hold the role → `RoleNotAssignedException`.
- [ ] **Last-super-admin guard:** if revoking `SUPER_ADMIN` and `countUsersWithRole(SUPER_ADMIN) <= 1` → `InsufficientPrivilegeException` ("cannot remove the last super-admin").
- [ ] Persist + audit (`"ROLE_REVOKED"`). `@Transactional`.

### `updateRolePermissions` (super-admin only)

- [ ] Caller must hold `SUPER_ADMIN` (enforced again here even though the endpoint is gated — defence in depth) → else `InsufficientPrivilegeException`.
- [ ] **Lockout guard:** refuse to remove `ROLE_PERMISSION_MANAGE` from the `SUPER_ADMIN` role → `ProtectedRoleException` (otherwise no one could ever edit permissions again).
- [ ] Resolve submitted `PermissionName`s to ids via `PermissionRepository`; unknown names → `IllegalArgumentException` / 400.
- [ ] `roleRepository.replaceRolePermissions(roleId, permissionIds)`.
- [ ] Audit (`"ROLE_PERMISSIONS_UPDATED"`, detail = the new permission set). `@Transactional`.

### Reads

- [ ] `getUserRoles` / `listRoles` — straight delegation to the repository.
- [ ] `getUserPermissions` — delegate to `findPermissionNamesByUserId`; return an empty set for a user with no roles (never null).

### `assignDefaultRole`

- [ ] Grant `USER` (resolved by name) to the given user id, `assignedBy = null` (system). No privilege guard. Skip if already assigned (idempotent). Wire this into the existing signup flow (`UserService` registration) so every new account gets `USER`.

---

## Notes

- **Why guards live here, not only in `@PreAuthorize`.** `hasAuthority('ROLE_ASSIGN')` answers "may this user assign *some* role" — it cannot express "an admin may not grant super-admin" or "don't delete the last super-admin." Those are domain invariants and belong in the service. The annotation is the outer gate; the service is the authority.
- **Audit everything.** Every mutating method writes an `audit_logs` row via the existing `AuditLogRepository`. This is a `NFR-007` requirement and the only forensic trail for privilege changes.
- **Reuse, don't duplicate, the actor lookup.** Loading the actor's roles for the privilege guard uses the same `RoleRepository.findRolesByUserId` the filter uses — no second mechanism.

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Privilege escalation** — why "who can grant what" must be constrained — https://owasp.org/www-community/attacks/Privilege_escalation
- **Defense in depth** — layering the annotation gate over the domain guard — https://owasp.org/www-community/Defense_in_Depth
- **Transaction boundaries** — keep the role change + audit write atomic — https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html

### Official docs (code reference)
- **Spring `@Transactional`** — https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html
- **Reference service** — see `backend/.../domain/service/PostService.java` in this repo
