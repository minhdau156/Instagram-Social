# TASK-9.33 — Admin RBAC pages & route registration

## Overview

Build the UI for runtime role management and wire its routes. A `RoleManagementPage` (super-admin only) lists roles and lets a super-admin toggle which permissions each role grants; the existing `AdminUsersPage` gains a per-user role-assignment panel. All actions use `rbacApi` (TASK-9.31) and are gated with `<PermissionGate>` (TASK-9.32).

---

## Prerequisites

Confirm these are done before starting:

- `frontend/src/api/rbacApi.ts` exists and exports `listRoles`, `listPermissions`, `updateRolePermissions`, `getUserRoles`, `assignRole`, `revokeRole` (TASK-9.31).
- `frontend/src/hooks/usePermissions.ts` exports `usePermissions()` (TASK-9.32).
- `frontend/src/components/common/PermissionGate.tsx` and `SuperAdminRoute.tsx` exist (TASK-9.32).
- `frontend/src/pages/admin/AdminUsersPage.tsx` exists (TASK-9.16).

---

## File Map

```
frontend/src/components/admin/RolePermissionEditor.tsx   ← create
frontend/src/components/admin/UserRolesPanel.tsx         ← create
frontend/src/pages/admin/RoleManagementPage.tsx          ← create
frontend/src/pages/admin/AdminUsersPage.tsx              ← modify
frontend/src/App.tsx                                     ← modify
```

---

## Step 1 — Create `RolePermissionEditor`

**File**: `frontend/src/components/admin/RolePermissionEditor.tsx`

### Props interface

```ts
interface RolePermissionEditorProps {
  role: Role;               // from src/types/rbac.ts
  allPermissions: Permission[];
  onSave: (permissions: PermissionName[]) => void;
  disabled?: boolean;
}
```

### State

Use a single `useState<PermissionName[]>` initialized from `role.permissions`. This is the "draft" set the user is editing before saving.

```ts
const [selected, setSelected] = useState<PermissionName[]>(role.permissions);
```

Reset the draft when the `role` prop changes (the parent may switch which role is being edited):

```ts
useEffect(() => {
  setSelected(role.permissions);
}, [role.id]);
```

### Rendering the permission list

Render each item in `allPermissions` as an MUI `<FormControlLabel control={<Checkbox />} />` inside a `<FormGroup>`.

For each permission:
- `checked`: `selected.includes(permission.name)`
- `onChange`: toggle the name in/out of `selected`
- `disabled`: pass the outer `disabled` prop — **plus** a special rule: if `role.name === 'SUPER_ADMIN'` and `permission.name === 'ROLE_PERMISSION_MANAGE'`, force `disabled={true}` and `checked={true}` regardless. Wrap that specific checkbox in an MUI `<Tooltip title="This permission cannot be removed from SUPER_ADMIN — the backend enforces it.">` so the user understands why it is locked.

### Save button

Render an MUI `<Button variant="contained">` labelled "Save permissions".

- `disabled` when `disabled` prop is true, or when there are no changes (`arraysEqual(selected, role.permissions)` — implement a simple sort-and-compare helper locally).
- `onClick`: call `onSave(selected)`.

### Layout

Wrap in an MUI `<Box>`. Use `<Typography variant="subtitle1">` for the role name as a heading. Put the `<FormGroup>` below, then the save button aligned to the right (`display: 'flex', justifyContent: 'flex-end', mt: theme.spacing(1)`).

Use `sx` with `theme.spacing()` and `theme.palette` only — no hardcoded hex or px values.

---

## Step 2 — Create `RoleManagementPage`

**File**: `frontend/src/pages/admin/RoleManagementPage.tsx`

### Route guard

Wrap the entire page output in `<SuperAdminRoute>`. Import it from `../../components/common/SuperAdminRoute`.

### Data fetching

Use two React Query queries:

```ts
const { data: roles, isLoading: rolesLoading, isError: rolesError } = useQuery({
  queryKey: ['admin', 'roles'],
  queryFn: rbacApi.listRoles,
});

const { data: allPermissions, isLoading: permsLoading } = useQuery({
  queryKey: ['admin', 'permissions'],
  queryFn: rbacApi.listPermissions,
});
```

### Mutation

Define one mutation for saving permissions:

```ts
const { mutate: savePermissions, isPending } = useMutation({
  mutationFn: ({ roleName, permissions }: { roleName: RoleName; permissions: PermissionName[] }) =>
    rbacApi.updateRolePermissions(roleName, permissions),
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['admin', 'roles'] });
    // show success toast
  },
  onError: (error) => {
    // extract server message from AxiosError and show error toast
  },
});
```

### `disabled` logic

Call `usePermissions()`. Set `editorDisabled = !hasPermission('ROLE_PERMISSION_MANAGE')`. Pass this to every `<RolePermissionEditor disabled={editorDisabled} />`.

### Loading & error states

- While `rolesLoading || permsLoading`: render a centered MUI `<CircularProgress />`.
- If `rolesError`: render an MUI `<Alert severity="error">Failed to load roles. Please refresh.</Alert>`.

### Layout

- Page title: `<Typography variant="h5">Roles & Permissions</Typography>`.
- Render one `<RolePermissionEditor>` per role in the `roles` array.
- Separate each editor with an MUI `<Divider sx={{ my: theme.spacing(3) }} />`.
- Pass `onSave={(permissions) => savePermissions({ roleName: role.name, permissions })}`.

---

## Step 3 — Create `UserRolesPanel`

**File**: `frontend/src/components/admin/UserRolesPanel.tsx`

### Props interface

```ts
interface UserRolesPanelProps {
  userId: string;
  onClose?: () => void;
}
```

### Data fetching

```ts
const { data: userRoles, isLoading, isError } = useQuery({
  queryKey: ['admin', 'users', userId, 'roles'],
  queryFn: () => rbacApi.getUserRoles(userId),
  enabled: !!userId,
});
```

### Mutations

Define two mutations:

```ts
const { mutate: assign, isPending: assigning } = useMutation({
  mutationFn: (roleName: RoleName) => rbacApi.assignRole(userId, roleName),
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users', userId, 'roles'] }),
  onError: (error) => { /* show error toast with server message */ },
});

const { mutate: revoke, isPending: revoking } = useMutation({
  mutationFn: (roleName: RoleName) => rbacApi.revokeRole(userId, roleName),
  onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users', userId, 'roles'] }),
  onError: (error) => { /* show error toast with server message */ },
});
```

### Rendering current roles

Show each role in `userRoles.roles` as an MUI `<Chip label={role.name} onDelete={...} />`.

Before calling `revoke`, if `role.name === 'ADMIN' || role.name === 'SUPER_ADMIN'`, show a confirmation — use a local `useState<RoleName | null>` for `pendingRevoke`. When the chip's `onDelete` fires, set `pendingRevoke = role.name`. Render a small MUI `<Dialog>` that asks "Are you sure you want to remove [role] from this user?" with Cancel / Confirm buttons. On Confirm, call `revoke(pendingRevoke)` and clear state.

For `USER` and `MODERATOR` roles, call `revoke` directly without a dialog.

### "Add role" control

Gate the entire add-role section with `<PermissionGate permission="ROLE_ASSIGN">`.

Inside the gate, render an MUI `<Select>` populated with all `RoleName` values not already assigned. On selection, call `assign(selectedRole)`. Disable the select while `assigning`.

### Error display

**Do not swallow errors.** The `onError` callbacks above must extract the server error message from the Axios error response body and call your project's toast utility. If the server returns a `403` with a message like `"Cannot escalate privileges"`, that message should appear in the toast.

### Loading & error states

- While loading: `<CircularProgress size={24} />`.
- On query error: `<Alert severity="error">Failed to load roles for this user.</Alert>`.

---

## Step 4 — Modify `AdminUsersPage`

**File**: `frontend/src/pages/admin/AdminUsersPage.tsx`

### Local state to add

```ts
const [rolesTarget, setRolesTarget] = useState<AdminUser | null>(null);
```

### Menu item to add

In the existing context menu (the `<Menu>` component that appears when clicking the actions button per row), add a new `<MenuItem>`:

```tsx
<MenuItem onClick={() => { setRolesTarget(menuAnchor!.user); setMenuAnchor(null); }}>
  Manage roles
</MenuItem>
```

Place it after the existing Suspend/Unsuspend item.

### Dialog to add

At the bottom of the JSX (before the closing fragment), add:

```tsx
<Dialog
  open={!!rolesTarget}
  onClose={() => setRolesTarget(null)}
  fullWidth
  maxWidth="sm"
>
  <DialogTitle>Roles — {rolesTarget?.username}</DialogTitle>
  <DialogContent>
    {rolesTarget && (
      <UserRolesPanel userId={rolesTarget.id} onClose={() => setRolesTarget(null)} />
    )}
  </DialogContent>
</Dialog>
```

Import `Dialog`, `DialogTitle`, `DialogContent` from `@mui/material`. Import `UserRolesPanel` from `../../components/admin/UserRolesPanel`.

---

## Step 5 — Register route in `App.tsx`

**File**: `frontend/src/App.tsx`

### Lazy import

Add alongside the existing admin lazy imports:

```ts
const RoleManagementPage = React.lazy(() => import('./pages/admin/RoleManagementPage'));
```

### Route entry

Mirror the existing admin route pattern exactly:

```tsx
<Route
  path="/admin/roles"
  element={
    <SuperAdminRoute>
      <ErrorBoundary>
        <RoleManagementPage />
      </ErrorBoundary>
    </SuperAdminRoute>
  }
/>
```

Place it next to the `/admin/users` route.

Import `SuperAdminRoute` from `./components/common/SuperAdminRoute`.

### Nav entry

In the admin shell nav (wherever `/admin/users` and `/admin/reports` links live), add:

```tsx
<PermissionGate role="SUPER_ADMIN">
  <NavLink to="/admin/roles">Roles & Permissions</NavLink>
</PermissionGate>
```

---

## Behaviour rules

**Separate the two gates.** Assigning a user a role requires `ROLE_ASSIGN` (admin-level). Editing what a role can do requires `ROLE_PERMISSION_MANAGE` (super-admin only). These are independent — the UI must check them independently and never conflate them.

**Never hide errors behind the gate.** The gate hides actions the user cannot perform. But if a permitted action returns a `403` anyway (e.g. an admin tries to grant `SUPER_ADMIN`), show the server's error message in a toast. The gate is a UX convenience; the server is the authority.

**No hardcoded colours or sizes.** Every `sx` prop must use `theme.spacing(...)` or `theme.palette.*`. No hex strings, no pixel literals.

---

## Acceptance criteria

- [x] `/admin/roles` is accessible only to users with the `SUPER_ADMIN` role; all others are redirected.
- [x] `RoleManagementPage` renders one `RolePermissionEditor` per role returned by `listRoles()`.
- [x] A user without `ROLE_PERMISSION_MANAGE` sees the editors in read-only (`disabled`) mode.
- [x] `SUPER_ADMIN`'s `ROLE_PERMISSION_MANAGE` checkbox is always checked and non-interactive, with a tooltip explaining why.
- [x] Saving permissions calls `updateRolePermissions` and invalidates the `['admin', 'roles']` query on success.
- [x] `AdminUsersPage` has a "Manage roles" menu item per row that opens a dialog.
- [x] The dialog shows current roles as chips and allows removal (with confirm for `ADMIN`/`SUPER_ADMIN`).
- [x] The "Add role" control is hidden for users without `ROLE_ASSIGN`.
- [x] Backend errors (403, 400) surface as toasts — they are never silently swallowed.
- [x] `RoleManagementPage` is lazy-loaded; the bundle does not include it in the main chunk.
