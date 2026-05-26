# TASK-9.25 — JPA entities & repositories (RBAC)

## Overview

Map the four RBAC tables from [TASK-9.19](TASK-9.19-rbac-flyway-migration.md) to JPA entities and Spring Data repositories. These live in the persistence package and must never leak outside it — the adapter ([TASK-9.26](TASK-9.26-rbac-persistence-adapter.md)) converts them to/from domain types. Lombok is allowed here (it is an adapter-layer class).

---

## File Locations

```
backend/src/main/java/com/instagram/adapter/out/persistence/RoleJpaEntity.java             ← create
backend/src/main/java/com/instagram/adapter/out/persistence/PermissionJpaEntity.java       ← create
backend/src/main/java/com/instagram/adapter/out/persistence/UserRoleJpaEntity.java         ← create
backend/src/main/java/com/instagram/adapter/out/persistence/RoleJpaRepository.java         ← create
backend/src/main/java/com/instagram/adapter/out/persistence/PermissionJpaRepository.java   ← create
backend/src/main/java/com/instagram/adapter/out/persistence/UserRoleJpaRepository.java     ← create
```

---

## Checklist

### `RoleJpaEntity`

- [x] `@Entity @Table(name = "roles")`. Fields: `id`, `name` (String), `description`, `system` (`@Column(name = "is_system")`), `createdAt`.
- [x] Map permissions with `@ManyToMany(fetch = FetchType.LAZY)` + `@JoinTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"), inverseJoinColumns = @JoinColumn(name = "permission_id"))` → `Set<PermissionJpaEntity>`.

### `PermissionJpaEntity`

- [x] `@Entity @Table(name = "permissions")`. Fields: `id`, `name`, `description`, `createdAt`.

### `UserRoleJpaEntity`

- [x] `@Entity @Table(name = "user_roles")` with a composite key (`@IdClass` or `@EmbeddedId`) over (`user_id`, `role_id`) — mirror the composite-key approach used by `ConversationMemberJpaEntity` / `UserBlockJpaEntity` so the codebase stays consistent.
- [x] Fields: `userId`, `roleId`, `assignedBy`, `assignedAt`. Store raw UUIDs (no `@ManyToOne` to `UserJpaEntity`) to avoid pulling the user graph — same pattern as `SearchHistoryJpaEntity`.

### `RoleJpaRepository`

- [x] `Optional<RoleJpaEntity> findByName(String name)`; `List<RoleJpaEntity> findAll()` (inherited).
- [x] `@EntityGraph(attributePaths = "permissions")` on `findByName` / a `findAllWithPermissions()` to fetch roles + permissions without N+1.

### `PermissionJpaRepository`

- [x] `Optional<PermissionJpaEntity> findByName(String name)`; `List<PermissionJpaEntity> findByIdIn(Collection<UUID> ids)`.

### `UserRoleJpaRepository`

- [x] `List<UserRoleJpaEntity> findByUserId(UUID userId)`.
- [x] `boolean existsByUserIdAndRoleId(UUID userId, UUID roleId)`.
- [x] `@Modifying @Transactional void deleteByUserIdAndRoleId(UUID userId, UUID roleId)`.
- [x] `long countByRoleId(UUID roleId)` — backs the last-super-admin guard.
- [x] A projection query for the hot path: `@Query("SELECT p.name FROM UserRoleJpaEntity ur JOIN RoleJpaEntity r ON r.id = ur.roleId JOIN r.permissions p WHERE ur.userId = :userId")` → `Set<String>` of permission names. Adjust the JPQL to your exact mappings; the goal is **one query** to fetch a user's permission names.

---

## Notes

- **N+1 is the trap here.** Loading a user's roles and then each role's permissions in separate queries is the classic N+1 ([TASK-10.4](../phase-10/TASK-10.4-n-plus-1-query-review.md)). Use `@EntityGraph` for role-with-permissions reads and the dedicated projection query for the per-request authority lookup.
- **Keep entities out of the domain.** `RoleJpaEntity` is never returned from a repository method that domain code calls — the adapter maps it. Enforced later by the ArchUnit rule in [TASK-10.38](../phase-10/TASK-10.38-archunit-fitness-tests.md).
- **`is_system` column name.** The DB column is `is_system`; the Java field is `system` (or `systemRole`) with an explicit `@Column(name = "is_system")` — don't rely on naming-strategy guesswork for booleans.

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **JPA `@ManyToMany` with a join table** — mapping role↔permission — https://www.baeldung.com/jpa-many-to-many
- **Composite keys (`@IdClass`/`@EmbeddedId`)** — for the `user_roles` PK — https://www.baeldung.com/jpa-composite-primary-keys
- **`@EntityGraph`** — fetch associations in one query — https://www.baeldung.com/jpa-entity-graph

### Official docs (code reference)
- **Spring Data JPA reference** — https://docs.spring.io/spring-data/jpa/reference/
- **Reference entities** — see `UserBlockJpaEntity` / `SearchHistoryJpaEntity` in this repo
