# TASK-9.17 — Admin Route Guard: AdminRoute

## Overview

Create `AdminRoute` — a route wrapper component that restricts access to admin-only pages. Users without `ROLE_ADMIN` are redirected to the home page with a toast notification informing them that access is denied. This component is analogous to the existing `ProtectedRoute` component (which only checks authentication), but adds an admin-role check on top.

---

## Requirements

- Lives in `frontend/src/components/common/` — it is a cross-cutting routing concern, not a feature-specific component.
- Named export (not default export) — consistent with other shared components in `common/`.
- Must read the current user's role from the auth context — never from a separate API call inside the guard.
- Must work with `React.lazy` — the guarded pages use lazy-loading, so the guard must render a `Suspense` fallback correctly when the child hasn't loaded yet.

---

## File Location

```
frontend/src/components/common/AdminRoute.tsx
```

---

## Checklist

### Prerequisites: Auth Context must expose `role`

- [x] Before implementing `AdminRoute`, verify that `AuthContext` (or the `useAuth()` hook) exposes the current user's role. Open `frontend/src/hooks/useAuth.ts` (or equivalent) and check whether the `profile` object includes an `isAdmin` flag or a `role` string.
- [x] If the role is not currently exposed:
  - Check whether the JWT payload includes a role claim. If not, the backend JWT generation (TASK-9.5 prerequisite) must be updated first.
  - If the JWT carries the role, update the JWT parsing logic in `useAuth` / `AuthContext` to extract and expose it. Add an `isAdmin: boolean` derived field: `isAdmin = profile?.role === 'ADMIN'` (or `roles.includes('ROLE_ADMIN')` depending on the JWT structure).
  - Do not add a new API call to fetch the role — derive it from the already-decoded JWT stored in context.
- [x] Add `isAdmin?: boolean` (or `role?: string`) to the TypeScript user profile type in `frontend/src/types/` to avoid `any` casts.

---

### `AdminRoute.tsx`

#### Props interface

- [x] `children: React.ReactNode` — the page component(s) to render when the user is an admin.

#### Hooks to call inside the component

- [x] `const { profile, isLoading } = useAuth()` — use whatever the existing auth hook exposes. The exact field names must match the current hook signature; do not rename them.
- [x] `const navigate = useNavigate()` from `react-router-dom`.

#### Logic

- [x] While `isLoading` is `true` (auth state is not yet resolved): render a centred `CircularProgress` fullscreen spinner — do not redirect while loading because the user's role has not yet been determined. This prevents a flash-redirect on page refresh for legitimate admins.
- [x] Once loading is complete:
  - If `profile` is `null` or `undefined` (user is not authenticated): redirect to `/login` using `<Navigate to="/login" replace />`. The standard `ProtectedRoute` already handles this case, but `AdminRoute` should be self-contained so it can be used independently.
  - If `profile` is present but `isAdmin` is `false` (user is authenticated but not an admin): show a toast/snackbar with the message "You don't have permission to access this page." then redirect to `/` using `<Navigate to="/" replace />`. The toast should appear briefly before the redirect happens — use a `useEffect` to show the toast on the first render when this condition is met, then redirect on the next render or after a short delay.
  - If `profile` is present and `isAdmin` is `true`: render `children`.

#### Snackbar/toast integration

- [x] Check how other components in the project display error toasts. If the project uses a global `Snackbar` or `notistack`, use that. If there is no shared toast system, render a local MUI `Snackbar` with `autoHideDuration={3000}` and then redirect after 1 second (enough time for the user to read the message before being redirected).
- [x] Alternatively, use the redirect immediately and let the landing page show a dismissable `Alert` component. Choose whichever approach is most consistent with the existing codebase.

#### Suspense integration

- [x] `AdminRoute` itself does not need to render a `Suspense` boundary — the route registration in `App.tsx` (TASK-9.18) wraps each lazy page in `Suspense`. `AdminRoute` just needs to render `{children}` when access is granted.

---

## Notes

- `AdminRoute` is a complement to the existing `ProtectedRoute`, not a replacement. The wrapping order in `App.tsx` should be: `ProtectedRoute` (authentication check) wrapping `AdminRoute` (role check) wrapping the lazy page. However, since `AdminRoute` already handles the unauthenticated case (redirecting to `/login`), they can also be used independently — document your chosen nesting approach in TASK-9.18.
- The frontend `AdminRoute` guard is a UX convenience, not a security boundary. The real security is enforced by:
  1. `SecurityConfig.java` — `requestMatchers("/api/v1/admin/**").hasRole("ADMIN")`.
  2. `@PreAuthorize("hasRole('ADMIN')")` on every `AdminService` method.
  A determined user who bypasses the frontend guard will receive a `403 FORBIDDEN` from every API call they attempt. The frontend guard only prevents the blank/broken page that would result from loading the admin UI without data.
- The redirect to `/` (instead of a dedicated "Access Denied" page) is a deliberate security-by-obscurity choice — it does not reveal to an attacker that an admin panel exists at a specific path.
