# TASK-9.22 — Out-ports: RoleRepository, PermissionRepository

## Overview

Define the driven ports (interfaces the domain owns, the persistence layer implements) for reading and writing RBAC data. These are pure-Java interfaces returning **domain model types** (`Role`, `Permission`, `User`) — never JPA entities. The persistence adapter in [TASK-9.26](TASK-9.26-rbac-persistence-adapter.md) implements them; the `RbacService` ([TASK-9.24](TASK-9.24-rbac-domain-service.md)) and the authority loader ([TASK-9.27](TASK-9.27-authorization-jwt-authorities.md)) depend on them.

---

## File Locations

```
backend/src/main/java/com/instagram/domain/port/out/RoleRepository.java        ← create
backend/src/main/java/com/instagram/domain/port/out/PermissionRepository.java  ← create
```

---

## Checklist

### `RoleRepository`

- [ ] `Optional<Role> findByName(RoleName name)`
- [ ] `Optional<Role> findById(UUID roleId)`
- [ ] `List<Role> findAll()` — all four system roles, each with its permissions populated.
- [ ] `Set<Role> findRolesByUserId(UUID userId)` — the roles a user holds, **with permissions eagerly populated** (the filter needs permissions in one call).
- [ ] `Set<PermissionName> findPermissionNamesByUserId(UUID userId)` — flattened permission names for a user; the hot path used on every authenticated request in [TASK-9.27](TASK-9.27-authorization-jwt-authorities.md). Provide this as a dedicated, projection-style method so the adapter can issue a single optimized query.
- [ ] `void assignRoleToUser(UUID userId, UUID roleId, UUID assignedBy)` — idempotency is the caller's concern (the service checks first); the adapter may also rely on the `user_roles` PK.
- [ ] `void revokeRoleFromUser(UUID userId, UUID roleId)`
- [ ] `boolean userHasRole(UUID userId, RoleName name)`
- [ ] `long countUsersWithRole(RoleName name)` — needed for the "cannot remove the last `SUPER_ADMIN`" guard.
- [ ] `void replaceRolePermissions(UUID roleId, Set<UUID> permissionIds)` — overwrite a role's permission set (used by the manage-permissions endpoint).

### `PermissionRepository`

- [ ] `List<Permission> findAll()`
- [ ] `Optional<Permission> findByName(PermissionName name)`
- [ ] `Set<Permission> findByIds(Collection<UUID> ids)` — resolve a submitted id set when replacing a role's permissions.

---

## Notes

- **Only allowed Spring import is `Pageable`** — and only if you decide to paginate `findAll`/user listings (system roles are few, so pagination is optional here). Otherwise these interfaces import nothing from Spring/JPA, matching `ModerationRepository` from [TASK-9.3](TASK-9.3-out-ports.md).
- **Return domain types, map inside the adapter.** `findRolesByUserId` returns `Set<Role>` (domain), never `RoleJpaEntity`. The `toDomain()` mapping is private to the adapter.
- **Why a dedicated `findPermissionNamesByUserId`.** Loading full `Role` graphs on every request is wasteful. A narrow query (`SELECT p.name FROM user_roles ... JOIN role_permissions ... JOIN permissions p WHERE user_id = ?`) keeps the per-request authorization cost to one indexed read — and is trivially cacheable in Redis later ([TASK-10.3](../phase-10/TASK-10.3-redis-caching.md)).

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Ports & Adapters (hexagonal)** — why the domain owns the interface and infra implements it — https://alistair.cockburn.us/hexagonal-architecture/
- **Dependency inversion** — depend on abstractions, not the JPA layer — https://martinfowler.com/articles/dipInTheWild.html

### Official docs (code reference)
- **Reference out-port** — see `backend/.../domain/port/out/PostRepository.java` in this repo
- **java.util.Optional** — https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/Optional.html
