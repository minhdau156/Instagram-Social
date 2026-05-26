# TASK-9.20 — Domain models: Role, Permission & User extension

## Overview

Model the RBAC concepts as **pure-Java domain objects** (no Spring, JPA, or Lombok — same rules as `Post.java` and the existing `User.java`). You create `Permission` and `Role` value types plus two enums that pin the *names* to a fixed, type-safe set, and you extend the existing `User` domain model so it carries the roles a user holds and can answer authorization questions (`hasRole`, `hasPermission`) without reaching into the framework.

---

## Concepts

- **Enum for names, object for data.** `RoleName` / `PermissionName` enums make role/permission *names* compile-time safe in domain code, while `Role` / `Permission` objects carry the id, description, and (for roles) the granted permissions loaded from the DB.
- **Behaviour lives in the entity.** Authorization predicates (`user.hasPermission(PermissionName.REPORT_REVIEW)`) belong on `User`, not scattered across services — consistent with `withSoftDelete()` / `isActive()` style already used here.
- **Immutability via copy.** Extend `User` using the same hand-written Builder + `copy()` pattern; add a `withRoles(...)` that returns a new instance.

---

## File Locations

```
backend/src/main/java/com/instagram/domain/model/RoleName.java          ← create (enum)
backend/src/main/java/com/instagram/domain/model/PermissionName.java    ← create (enum)
backend/src/main/java/com/instagram/domain/model/Permission.java        ← create
backend/src/main/java/com/instagram/domain/model/Role.java              ← create
backend/src/main/java/com/instagram/domain/model/User.java              ← modify (add roles + helpers)
```

---

## Checklist

### `RoleName` enum

- [x] Values: `USER`, `MODERATOR`, `ADMIN`, `SUPER_ADMIN` — exactly matching the seeded `roles.name` values from [TASK-9.19](TASK-9.19-rbac-flyway-migration.md).

### `PermissionName` enum

- [x] Values matching the seeded `permissions.name` set: `REPORT_VIEW`, `REPORT_REVIEW`, `CONTENT_MODERATE`, `USER_VIEW`, `USER_SUSPEND`, `USER_UNSUSPEND`, `AUDIT_LOG_VIEW`, `ROLE_VIEW`, `ROLE_ASSIGN`, `ROLE_PERMISSION_MANAGE`.

### `Permission`

- [x] Fields: `UUID id`, `PermissionName name`, `String description`.
- [x] Hand-written Builder; plain getters; no setters.

### `Role`

- [x] Fields: `UUID id`, `RoleName name`, `String description`, `boolean system`, `Set<Permission> permissions`.
- [x] Hand-written Builder; `permissions` defaults to an empty `Set` in `build()` if null.
- [x] Method `boolean grants(PermissionName permission)` — true if any permission in the set matches.

### `User` (modify)

- [x] Add field `Set<Role> roles` (default empty set).
- [x] Add it to the Builder and to the private `copy()` so existing `withXxx()` methods preserve roles.
- [x] Add `User withRoles(Set<Role> roles)` returning a new instance via `copy()`.
- [x] Add `boolean hasRole(RoleName roleName)` — true if any held role has that name.
- [x] Add `boolean hasPermission(PermissionName permission)` — true if **any** held role `grants(permission)`.
- [x] Add `Set<PermissionName> permissionNames()` — the flattened, de-duplicated set of all permissions across the user's roles (used to build authorities in [TASK-9.27](TASK-9.27-authorization-jwt-authorities.md)).

---

## Notes

- **Keep it framework-free.** No `@Entity`, no Lombok — these are domain models. The JPA mapping is a separate concern handled in [TASK-9.25](TASK-9.25-rbac-jpa-entities-repositories.md).
- **Don't break existing `User` construction.** Many call sites build `User` via the Builder without roles. Defaulting `roles` to an empty set in `build()` keeps them compiling; a user with no loaded roles simply has no permissions.
- **`hasPermission` is the primitive everything else uses.** Method-security checks ([TASK-9.28](TASK-9.28-securityconfig-method-security.md)) and the privilege-escalation guard ([TASK-9.24](TASK-9.24-rbac-domain-service.md)) all reduce to this method — keep it correct and simple.

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Domain modelling & rich behaviour** — why predicates like `hasPermission` belong on the entity — https://martinfowler.com/bliki/AnemicDomainModel.html
- **Java enums (with fields/methods)** — type-safe fixed sets — https://docs.oracle.com/javase/tutorial/java/javaOO/enum.html
- **Immutable objects in Java** — the copy-on-write style used here — https://docs.oracle.com/javase/tutorial/essential/concurrency/immutable.html

### Official docs (code reference)
- **java.util.Set / Collectors.toSet** — flattening permissions across roles — https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/stream/Collectors.html
- **Reference entity pattern** — see `backend/.../domain/model/Post.java` in this repo
