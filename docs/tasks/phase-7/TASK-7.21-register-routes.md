# TASK-7.21 — Register Routes

## Overview

Add two protected, lazily-loaded routes to the app router: `/notifications` and `/settings/notifications`. Follow the exact pattern used in phase 6 for the messaging routes.

## Requirements

- Both routes must be protected — wrap with whatever `ProtectedRoute` component the project already uses.
- Use `React.lazy` + `Suspense` for lazy loading.
- Both page files must use `export default` — React.lazy requires it (confirm before registering).

## File Location

```
frontend/src/App.tsx    (modify)
```

---

## Instructions

**Step 1 — Lazy-import the two pages at the top of `App.tsx`:**
Use `React.lazy` with a dynamic `import()` for each page, pointing at `'./pages/notifications/NotificationsPage'` and `'./pages/notifications/NotificationSettingsPage'`.

**How `React.lazy` works:**
`React.lazy` takes a function that returns a dynamic `import()`. When Vite (or Webpack) sees `import(...)` inside `React.lazy`, it splits that module into a separate JavaScript bundle. The browser only downloads that bundle when the user navigates to that route for the first time, keeping the initial page load lighter. This is called code-splitting. `React.lazy` requires the target module to have a default export — a named export will cause a runtime error.

**Step 2 — Add routes inside the router:**
Find where other protected routes are declared in `App.tsx` — look for existing `ProtectedRoute` usages such as the messaging routes from phase 6. Add two new `Route` elements following the same structure:
- Path `/notifications` — wraps `NotificationsPage` in a `ProtectedRoute` and a `Suspense`.
- Path `/settings/notifications` — wraps `NotificationSettingsPage` in a `ProtectedRoute` and a `Suspense`.

**What `Suspense` does and why it is required:**
While a lazy bundle is downloading, React cannot yet render the component. `Suspense` tells React what to show in the meantime — a loading spinner or screen. Without `Suspense` wrapping a lazy component, React throws an error. Use whatever loading fallback (`LoadingScreen`, `CircularProgress`, etc.) the other lazy routes in `App.tsx` already use — keep it consistent.

**Step 3 — (Optional) Link to the settings page:**
If the app has a user profile dropdown or a settings menu, add an entry pointing to `/settings/notifications`.

## Notes

- Look at how `InboxPage` and `ChatPage` are registered in `App.tsx` (phase 6) — copy that exact structure and substitute the new paths and component names.
- A named export (`export function NotificationsPage`) will cause a runtime error: React.lazy expects a default export. Confirm both page files start with `export default function`.
