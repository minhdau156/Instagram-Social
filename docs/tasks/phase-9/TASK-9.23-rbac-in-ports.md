# TASK-9.23 — In-ports: RBAC use-case interfaces

## Overview

Define the driving ports for managing roles and permissions — one interface per use case, one method per interface, each with an inner `Command` or `Query` record, exactly like the moderation in-ports from [TASK-9.4](TASK-9.4-in-ports.md). These are implemented by `RbacService` ([TASK-9.24](TASK-9.24-rbac-domain-service.md)) and called by the RBAC controller ([TASK-9.29](TASK-9.29-rbac-rest-controllers-dtos.md)) and the authority loader ([TASK-9.27](TASK-9.27-authorization-jwt-authorities.md)).

---

## File Locations

```
backend/src/main/java/com/instagram/domain/port/in/rbac/AssignRoleToUserUseCase.java       ← create
backend/src/main/java/com/instagram/domain/port/in/rbac/RevokeRoleFromUserUseCase.java     ← create
backend/src/main/java/com/instagram/domain/port/in/rbac/GetUserRolesUseCase.java           ← create
backend/src/main/java/com/instagram/domain/port/in/rbac/GetUserPermissionsUseCase.java     ← create
backend/src/main/java/com/instagram/domain/port/in/rbac/ListRolesUseCase.java              ← create
backend/src/main/java/com/instagram/domain/port/in/rbac/UpdateRolePermissionsUseCase.java  ← create
backend/src/main/java/com/instagram/domain/port/in/rbac/AssignDefaultRoleUseCase.java      ← create
```

---

## Checklist

- [ ] `AssignRoleToUserUseCase` — `Command(UUID actorId, UUID targetUserId, RoleName roleName)`; returns the updated `Set<Role>` (or `void`). `actorId` is the admin performing the action (for privilege checks + audit).
- [ ] `RevokeRoleFromUserUseCase` — `Command(UUID actorId, UUID targetUserId, RoleName roleName)`.
- [ ] `GetUserRolesUseCase` — `Query(UUID targetUserId)` → `Set<Role>` (with permissions populated).
- [ ] `GetUserPermissionsUseCase` — `Query(UUID userId)` → `Set<PermissionName>`. Used by the security filter to build authorities; keep it lightweight.
- [ ] `ListRolesUseCase` — no args (or an empty marker) → `List<Role>` with their permissions; powers the role-management UI.
- [ ] `UpdateRolePermissionsUseCase` — `Command(UUID actorId, RoleName roleName, Set<PermissionName> permissions)`; the **super-admin-only** operation that overwrites a role's permission set.
- [ ] `AssignDefaultRoleUseCase` — `Command(UUID userId)`; grants `USER` to a freshly registered account. Called from the signup flow so new users always have a role.

---

## Notes

- **`actorId` on mutating commands, not on queries.** Assignment/revocation/permission-edits need to know *who* is acting (for the privilege-escalation guard and audit log). Read queries (`GetUserRoles`, `ListRoles`) don't — Spring Security already gated the endpoint.
- **Why a separate `AssignDefaultRoleUseCase`.** New-user role assignment is a distinct trigger (signup) from admin-driven assignment, has no `actorId`, and skips the privilege guard. Keeping it separate avoids overloading `AssignRoleToUserUseCase` with a "system" caller special-case.
- **One method per interface.** Do not bundle assign+revoke into a single `ManageRolesUseCase` — follow the established one-use-case-per-interface convention.

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Use-case / interactor interfaces** — driving ports in clean architecture — https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html
- **Java records for commands** — concise immutable input DTOs — https://docs.oracle.com/en/java/javase/21/language/records.html

### Official docs (code reference)
- **Reference in-port** — see `backend/.../domain/port/in/CreatePostUseCase.java` in this repo
