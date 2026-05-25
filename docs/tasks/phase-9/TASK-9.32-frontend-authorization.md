# TASK-9.32 — Frontend authorization primitives

## Overview

Give the React app a single, declarative way to gate UI on the current user's grants. Add a `usePermissions` hook (fetches `GET /users/me/permissions` and caches it), a `<PermissionGate>` component for conditionally rendering actions, and a `<SuperAdminRoute>` guard alongside the existing `AdminRoute` ([TASK-9.17](TASK-9.17-admin-route-guard.md)). This replaces hardcoded "is this an admin?" checks with real permission checks.

---

## File Locations

```
frontend/src/hooks/usePermissions.ts                       ← create
frontend/src/components/common/PermissionGate.tsx          ← create
frontend/src/components/common/SuperAdminRoute.tsx         ← create
frontend/src/components/common/AdminRoute.tsx              ← modify (make permission-aware)
```

---

## Checklist

### `usePermissions` hook

- [ ] Uses React Query (`useQuery`) keyed `['me', 'grants']` to fetch `rbacApi.getMyGrants()`; `staleTime` ~5 min.
- [ ] Returns `{ roles, permissions, hasPermission, hasRole, hasAnyRole, isLoading }`.
- [ ] `hasPermission(p: PermissionName): boolean` and `hasRole(r: RoleName): boolean` are pure lookups over the fetched sets.
- [ ] While loading, gates should default to **denied** (return `false`) — never flash privileged UI before grants resolve.

### `<PermissionGate>`

- [ ] Props: `permission?: PermissionName | PermissionName[]`, `role?: RoleName | RoleName[]`, `requireAll?: boolean`, `fallback?: ReactNode`, `children`.
- [ ] Renders `children` only if the current user satisfies the requirement (any-of by default, all-of when `requireAll`); otherwise renders `fallback ?? null`.
- [ ] Pure presentational gate — it calls `usePermissions`, no data mutation.

### `<SuperAdminRoute>`

- [ ] Mirror `AdminRoute`: if the user lacks `SUPER_ADMIN`, redirect to `/` with a toast error; otherwise render `<Outlet />` / children.

### `AdminRoute` (modify)

- [ ] Change the gate from "is admin" to "has any admin-surface role" — `hasAnyRole(['MODERATOR','ADMIN','SUPER_ADMIN'])` — so moderators can reach the admin shell, while individual pages/actions inside are gated by permission via `<PermissionGate>`.

---

## Notes

- **The backend is the real gate.** These primitives are **UX only** — they hide buttons the user can't use. Every gated action still calls an endpoint that re-checks the permission server-side ([TASK-9.28](TASK-9.28-securityconfig-method-security.md)). Never treat the client check as security.
- **Default-deny while loading.** A gate that returns `true` before grants load will briefly show admin controls to everyone — a classic UI authz bug. Return `false` until `isLoading` is done.
- **One source of truth.** Don't decode the JWT in the browser to read roles — call `getMyGrants()`. The token's claims may be stale relative to runtime permission edits ([TASK-9.27](TASK-9.27-authorization-jwt-authorities.md), Approach A); the `/me/permissions` endpoint reflects the DB.

---

## Learning Resources

> New to these concepts? Skim the *Concepts* links first, then keep the *Official docs* open while you work.

### Concepts to learn
- **Client-side authorization is UX, not security** — why the server must re-check — https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html
- **Protected routes in React Router** — guard + redirect pattern — https://reactrouter.com/start/library/routing
- **React Query for server state** — caching the grants lookup — https://tanstack.com/query/latest/docs/framework/react/overview

### Official docs (code reference)
- **React Router** — https://reactrouter.com/
- **Reference guard** — see `frontend/src/components/common/AdminRoute.tsx` (TASK-9.17) in this repo
