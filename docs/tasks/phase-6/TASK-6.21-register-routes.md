# TASK-6.21 — Register Routes

## Overview

Register the two messaging routes in `App.tsx` using the same lazy-loading and protected route pattern established by previous phases.

## Requirements

- Modify `frontend/src/App.tsx` — do **not** create a new router file.
- Use `React.lazy` + `Suspense` for code splitting (same as `/` and `/explore` routes added in TASK-5.17).
- Both routes are protected — redirect to `/login` if unauthenticated.

## File to Modify

```
frontend/src/App.tsx
```

---

## Instructions

### Lazy imports to add

Add these two lazy imports alongside the existing ones at the top of `App.tsx`:

```
const InboxPage   = React.lazy(() => import('./pages/messaging/InboxPage'))
const ChatPage    = React.lazy(() => import('./pages/messaging/ChatPage'))
```

### Routes to add

Add inside the existing router (after the `/explore` route or grouped with other protected routes):

| Path | Component | Notes |
|------|-----------|-------|
| `/messages` | `InboxPage` | Protected. Desktop shows split-panel inline. |
| `/messages/:conversationId` | `ChatPage` | Protected. Used for mobile full-screen view. |

### Protected route pattern

Follow the exact same protected route wrapper already used in `App.tsx`. Do not introduce a new pattern.

### Suspense boundary

Both routes should be wrapped in a `<Suspense fallback={...}>` boundary. Use the same fallback component already used for existing lazy routes (likely a `<CircularProgress>` or a full-page skeleton).

## Notes

- `ChatPage` at `/messages/:conversationId` reads the `conversationId` from `useParams()` — it does not take props when used as a route (only when embedded inline in `InboxPage` on desktop).
- Verify the route renders correctly by navigating to `http://localhost:5173/messages` in the browser after adding.
- Keep the route order consistent with the existing routes in `App.tsx` — protected routes together, public routes together.
