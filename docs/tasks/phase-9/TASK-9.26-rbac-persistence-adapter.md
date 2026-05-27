# TASK-9.26 — Persistence adapter: RbacPersistenceAdapter

## Overview

Implement the RBAC out-ports against the JPA repositories, containing **all** mapping between JPA entities and domain models in private `toDomain()` helpers — the same pattern as `PostPersistenceAdapter` and `ModerationPersistenceAdapter` ([TASK-9.7](TASK-9.7-persistence-adapters.md)).

---

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/RbacPersistenceAdapter.java   ← create
```

> One adapter implementing both `RoleRepository` and `PermissionRepository` is fine (they share the same JPA repositories). Split into two classes only if it grows unwieldy.

---

## Checklist

- [x] `@Component`; constructor-inject `RoleJpaRepository`, `PermissionJpaRepository`, `UserRoleJpaRepository` (all `final`).
- [x] `implements RoleRepository, PermissionRepository`.
- [x] Implement every method from [TASK-9.22](TASK-9.22-rbac-out-ports.md).
- [x] `findRolesByUserId` — read `UserRoleJpaEntity` rows for the user, load each `RoleJpaEntity` with its permissions (use the `@EntityGraph` finder, or batch-load to avoid N+1), map to `Set<Role>`.
- [x] `findPermissionNamesByUserId` — call the projection query and map `Set<String>` → `Set<PermissionName>` via `PermissionName.valueOf(...)`.
- [x] `assignRoleToUser` — build and `save` a `UserRoleJpaEntity` (`userId`, `roleId`, `assignedBy`, `assignedAt = OffsetDateTime.now()`).
- [x] `revokeRoleFromUser` — `deleteByUserIdAndRoleId`.
- [x] `replaceRolePermissions(roleId, permissionIds)` — load the `RoleJpaEntity`, set its `permissions` to the resolved `PermissionJpaEntity` set, `save`. (JPA reconciles the `role_permissions` join rows.)
- [x] `countUsersWithRole(name)` → resolve role id, then `userRoleJpaRepository.countByRoleId`.
- [x] Private `toDomain(RoleJpaEntity)`, `toDomain(PermissionJpaEntity)` mappers; convert `String name` → `RoleName.valueOf` / `PermissionName.valueOf`.

---

## Notes

- **Enum mapping at the boundary.** The DB stores role/permission names as `VARCHAR`; the domain uses enums. Convert with `valueOf` in the adapter. A name in the DB that has no matching enum constant is a config drift bug — let `valueOf` throw rather than silently dropping it.
- **No JPA entity escapes.** Every public method returns a domain type (`Role`, `Permission`, `Set<PermissionName>`), never a `*JpaEntity`.
- **`@Transactional` placement.** Multi-step writes (`replaceRolePermissions`) are already wrapped by the service's `@Transactional` ([TASK-9.24](TASK-9.24-rbac-domain-service.md)); the adapter does not need its own unless used outside a service transaction.

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Adapter pattern (persistence)** — translating between storage and domain shapes — https://alistair.cockburn.us/hexagonal-architecture/
- **Mapping JPA `@ManyToMany` updates** — replacing a collection reconciles the join table — https://www.baeldung.com/jpa-many-to-many

### Official docs (code reference)
- **Reference adapter** — see `backend/.../adapter/out/persistence/PostPersistenceAdapter.java` in this repo
- **Spring Data JPA reference** — https://docs.spring.io/spring-data/jpa/reference/
