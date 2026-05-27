# TASK-9.31 — Frontend: TypeScript types & API services (RBAC)

## Overview

Add the data contracts and API layer the role-management UI needs. Define strict TypeScript types for roles/permissions and an `rbacApi.ts` service hitting the endpoints from [TASK-9.29](TASK-9.29-rbac-rest-controllers-dtos.md), using the shared Axios instance and unwrapping the `ApiResponse` envelope — same conventions as `adminApi.ts` ([TASK-9.13](TASK-9.13-api-services.md)).

---

## File Locations

```
frontend/src/types/rbac.ts        ← create
frontend/src/api/rbacApi.ts        ← create
```

---

## Checklist

### `types/rbac.ts`

- [x] `export type RoleName = 'USER' | 'MODERATOR' | 'ADMIN' | 'SUPER_ADMIN';`
- [x] `export type PermissionName =` string-literal union of the 10 permissions: `'REPORT_VIEW' | 'REPORT_REVIEW' | 'CONTENT_MODERATE' | 'USER_VIEW' | 'USER_SUSPEND' | 'USER_UNSUSPEND' | 'AUDIT_LOG_VIEW' | 'ROLE_VIEW' | 'ROLE_ASSIGN' | 'ROLE_PERMISSION_MANAGE';`
- [x] `export interface Permission { id: string; name: PermissionName; description: string; }`
- [x] `export interface Role { id: string; name: RoleName; description: string; system: boolean; permissions: PermissionName[]; }`
- [x] `export interface UserRoles { userId: string; roles: Role[]; }`
- [x] `export interface MyGrants { roles: RoleName[]; permissions: PermissionName[]; }` — shape of `GET /users/me/permissions`.
- [x] No `any`. These unions are the single source of truth the UI gates on.

### `api/rbacApi.ts`

- [x] `listRoles(): Promise<Role[]>` → `GET /api/v1/admin/roles`
- [x] `listPermissions(): Promise<Permission[]>` → `GET /api/v1/admin/permissions`
- [x] `updateRolePermissions(roleName: RoleName, permissions: PermissionName[]): Promise<Role>` → `PUT /api/v1/admin/roles/${roleName}/permissions`
- [x] `getUserRoles(userId: string): Promise<UserRoles>` → `GET /api/v1/admin/users/${userId}/roles`
- [x] `assignRole(userId: string, roleName: RoleName): Promise<UserRoles>` → `POST /api/v1/admin/users/${userId}/roles`
- [x] `revokeRole(userId: string, roleName: RoleName): Promise<void>` → `DELETE /api/v1/admin/users/${userId}/roles/${roleName}`
- [x] `getMyGrants(): Promise<MyGrants>` → `GET /api/v1/users/me/permissions`
- [x] All functions `async`, use the shared `api` Axios instance, and unwrap `data.data` exactly like the other service files.

---

## Notes

- **Keep the unions in sync with the backend enums.** `RoleName` / `PermissionName` must mirror `RoleName.java` / `PermissionName.java` ([TASK-9.20](TASK-9.20-rbac-domain-models.md)). A mismatch is a silent authorization bug in the UI — consider a comment pointing at the backend enums.
- **No direct `fetch`.** Use the `api` client so the JWT interceptor and error handling apply.

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **String-literal union types** — type-safe enums in TypeScript — https://www.typescriptlang.org/docs/handbook/2/everyday-types.html#literal-types
- **Typed Axios responses** — `api.get<T>()` and unwrapping envelopes — https://axios-http.com/docs/req_config

### Official docs (code reference)
- **TypeScript handbook** — https://www.typescriptlang.org/docs/
- **Reference API service** — see `frontend/src/api/adminApi.ts` (TASK-9.13) in this repo
