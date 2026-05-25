# TASK-9.33 — Admin RBAC pages & route registration

## Overview

Build the UI for runtime role management and wire its routes. A `RoleManagementPage` (super-admin only) lists roles and lets a super-admin toggle which permissions each role grants; the existing `AdminUsersPage` ([TASK-9.16](TASK-9.16-admin-panel-pages.md)) gains a per-user role-assignment panel. All actions use `rbacApi` ([TASK-9.31](TASK-9.31-rbac-types-api-services.md)) and are gated with `<PermissionGate>` ([TASK-9.32](TASK-9.32-frontend-authorization.md)).

---

## File Locations

```
frontend/src/pages/admin/RoleManagementPage.tsx              ← create
frontend/src/components/admin/UserRolesPanel.tsx             ← create
frontend/src/components/admin/RolePermissionEditor.tsx       ← create
frontend/src/pages/admin/AdminUsersPage.tsx                  ← modify (add UserRolesPanel)
frontend/src/App.tsx                                         ← modify (register route)
```

---

## Checklist

### `RolePermissionEditor` (component)

- [ ] Props: `role: Role`, `allPermissions: Permission[]`, `onSave(permissions: PermissionName[])`, `disabled?`.
- [ ] Render the full permission list as checkboxes; checked = the role currently grants it.
- [ ] System-role safety: when editing `SUPER_ADMIN`, render `ROLE_PERMISSION_MANAGE` as checked-and-disabled (the backend refuses to remove it — [TASK-9.24](TASK-9.24-rbac-domain-service.md)); surface that as a tooltip.
- [ ] Save button calls `rbacApi.updateRolePermissions`; optimistic or invalidate-on-success via React Query.

### `RoleManagementPage`

- [ ] Wrapped by `<SuperAdminRoute>`. Fetch `listRoles()` + `listPermissions()`.
- [ ] One `RolePermissionEditor` per role (USER/MODERATOR/ADMIN/SUPER_ADMIN). Loading + error states.
- [ ] Read-only (`disabled`) unless the viewer holds `ROLE_PERMISSION_MANAGE`.

### `UserRolesPanel` (component, used inside `AdminUsersPage`)

- [ ] Props: `userId: string`. Fetch `getUserRoles(userId)`.
- [ ] Show current roles as removable chips; an "Add role" control assigns a role (`assignRole`) — wrapped in `<PermissionGate permission="ROLE_ASSIGN">`.
- [ ] Removing a chip calls `revokeRole`; confirm before removing `ADMIN`/`SUPER_ADMIN`.
- [ ] Surface backend errors (e.g. 403 privilege-escalation, last-super-admin guard) as a toast — don't swallow them.

### `AdminUsersPage` (modify)

- [ ] Add a "Manage roles" action per row that opens `UserRolesPanel` (dialog or expand row).

### Route registration (`App.tsx`)

- [ ] Add `/admin/roles` → `RoleManagementPage`, lazy-imported via `React.lazy`, wrapped in `<SuperAdminRoute>` and `<ErrorBoundary>` — mirror the existing admin route registration from [TASK-9.18](TASK-9.18-register-routes.md).
- [ ] Add a "Roles & Permissions" nav entry in the admin shell, gated with `<PermissionGate role="SUPER_ADMIN">`.

---

## Notes

- **MUI + theme tokens only.** Use `sx` with `theme.spacing()` / `theme.palette`; no hardcoded hex/px — same rule as the rest of the frontend.
- **Don't hide errors behind the gate.** The UI hides actions the user can't perform, but if a permitted action still 403s (e.g. an admin tries to grant super-admin), show the server message. The gate is convenience; the server is truth.
- **Keep assignment and permission-editing separate.** Assigning a *user* a role (`ROLE_ASSIGN`, admin) is a different permission from editing what a *role* grants (`ROLE_PERMISSION_MANAGE`, super-admin). The UI must reflect both gates independently.

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Admin UI / permission matrix patterns** — editing role→permission grids — https://www.patternfly.org/patterns/ (data-driven tables)
- **React Query mutations + invalidation** — refresh after assign/revoke/edit — https://tanstack.com/query/latest/docs/framework/react/guides/mutations
- **Lazy routes & code-splitting** — `React.lazy` for admin pages — https://react.dev/reference/react/lazy

### Official docs (code reference)
- **MUI components** — https://mui.com/material-ui/all-components/
- **Reference pages** — see `frontend/src/pages/admin/AdminUsersPage.tsx` (TASK-9.16) in this repo
