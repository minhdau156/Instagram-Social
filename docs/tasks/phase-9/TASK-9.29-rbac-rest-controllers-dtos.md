# TASK-9.29 — REST controllers & DTOs (role management)

## Overview

Expose the runtime role/permission management API. Add a `RoleAdminController` for listing roles, viewing/editing a role's permissions, and assigning/revoking a user's roles. Controllers contain no business logic — they delegate to the RBAC use cases ([TASK-9.23](TASK-9.23-rbac-in-ports.md)) and return the `ApiResponse<T>` envelope, exactly like `AdminController` ([TASK-9.9](TASK-9.9-rest-controllers.md)).

---

## File Locations

```
backend/src/main/java/com/instagram/adapter/in/web/RoleAdminController.java            ← create
backend/src/main/java/com/instagram/adapter/in/web/dto/AssignRoleRequest.java          ← create
backend/src/main/java/com/instagram/adapter/in/web/dto/UpdateRolePermissionsRequest.java ← create
backend/src/main/java/com/instagram/adapter/in/web/dto/RoleResponse.java               ← create
backend/src/main/java/com/instagram/adapter/in/web/dto/PermissionResponse.java         ← create
backend/src/main/java/com/instagram/adapter/in/web/dto/UserRolesResponse.java          ← create
```

---

## Checklist

### `RoleAdminController`

- [ ] `@RestController @RequestMapping("/api/v1/admin")`, `@Tag(name = "Admin · Roles")`.
- [ ] Constructor-inject (all `final`): `ListRolesUseCase`, `UpdateRolePermissionsUseCase`, `AssignRoleToUserUseCase`, `RevokeRoleFromUserUseCase`, `GetUserRolesUseCase`.
- [ ] Use the existing `currentUserId()` helper for the acting admin's id (passed as `actorId`).

#### Endpoints

- [ ] `GET /roles` → `ListRolesUseCase`; returns `List<RoleResponse>` (each with its permissions). `@PreAuthorize("hasAuthority('ROLE_VIEW')")`.
- [ ] `GET /permissions` → `List<PermissionResponse>` of all assignable permissions (for the editing UI). `ROLE_VIEW`.
- [ ] `PUT /roles/{roleName}/permissions` → body `UpdateRolePermissionsRequest`; calls `UpdateRolePermissionsUseCase` with `currentUserId()`. `hasAuthority('ROLE_PERMISSION_MANAGE')`. Returns the updated `RoleResponse`.
- [ ] `GET /users/{id}/roles` → `GetUserRolesUseCase`; returns `UserRolesResponse`. `ROLE_VIEW`.
- [ ] `POST /users/{id}/roles` → body `AssignRoleRequest`; calls `AssignRoleToUserUseCase(currentUserId(), id, roleName)`. `ROLE_ASSIGN`. Returns `200/201` with the user's new role set.
- [ ] `DELETE /users/{id}/roles/{roleName}` → `RevokeRoleFromUserUseCase`. `ROLE_ASSIGN`. Returns `204`.

### DTOs (records)

- [ ] `AssignRoleRequest(@NotNull RoleName roleName)`.
- [ ] `UpdateRolePermissionsRequest(@NotNull Set<PermissionName> permissions)`.
- [ ] `RoleResponse(UUID id, RoleName name, String description, boolean system, Set<PermissionName> permissions)` + static `from(Role)`.
- [ ] `PermissionResponse(UUID id, PermissionName name, String description)` + static `from(Permission)`.
- [ ] `UserRolesResponse(UUID userId, Set<RoleResponse> roles)` + factory.

### Frontend convenience — expose the caller's own grants

- [ ] Add `GET /api/v1/users/me/permissions` (in `UserController` or here) returning the current user's `Set<RoleName>` + `Set<PermissionName>`. The frontend uses this to gate UI ([TASK-9.32](TASK-9.32-frontend-authorization.md)) — authenticated, no special permission required (a user may always read their own grants).

---

## Notes

- **`RoleName` / `PermissionName` as path/body params.** Spring binds a request string to an enum automatically when the value matches a constant; an unknown value yields a 400 (or map it to `RoleNotFoundException`). Keep the JSON values UPPERCASE to match the enum constants.
- **Reuse `AdminController`'s patterns.** Same envelope, same `currentUserId()`, same Swagger style. Don't invent a new response shape.
- **Privilege escalation is enforced in the service, not the controller.** The controller passes `actorId`; `RbacService` decides whether that actor may grant the requested role ([TASK-9.24](TASK-9.24-rbac-domain-service.md)). The controller stays dumb.

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **REST resource design for sub-collections** — `/users/{id}/roles` modelling — https://restfulapi.net/resource-naming/
- **Enum binding in Spring MVC** — string → enum for params/bodies — https://www.baeldung.com/spring-enum-request-param

### Official docs (code reference)
- **Spring `@RestController` / request mapping** — https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-controller/ann-requestmapping.html
- **Reference controller** — see `backend/.../adapter/in/web/AdminController.java` (TASK-9.9) in this repo
