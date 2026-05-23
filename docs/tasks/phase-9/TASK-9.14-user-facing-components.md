# TASK-9.14 — User-Facing Components: ReportDialog, BlockButton

## Overview

Create two user-facing moderation components. `ReportDialog` is a modal form that allows any authenticated user to report a post, comment, user, or message. `BlockButton` is a control shown in user profile kebab menus that allows the current user to block or unblock another user with a confirmation step. Both components delegate their API calls to React Query mutations.

---

## Requirements

- Both live in `frontend/src/components/moderation/`.
- MUI components and `sx` prop only — no inline `style={{}}`, no hardcoded colour values.
- Fully typed — no `any`.
- Named exports (not default exports) — they are shared components, not page-level components.
- Each component calls the appropriate API function through a React Query mutation — never calls `moderationApi` directly from event handlers.

---

## File Locations

```
frontend/src/components/moderation/ReportDialog.tsx
frontend/src/components/moderation/BlockButton.tsx
```

---

## Checklist

### Custom hooks prerequisite

Before building these components, create the following React Query hooks in `frontend/src/hooks/moderation/`:

- [ ] `useSubmitReport.ts` — wraps `useMutation` calling `moderationApi.submitReport(payload)`. On success, show a success toast or snackbar ("Report submitted. Thank you."). On error, show an error toast.
- [ ] `useBlockUser.ts` — wraps `useMutation` calling `moderationApi.blockUser(username)`. On success, invalidate the user profile query (so the `isBlocked` state refreshes) and show a toast. On error, show an error toast.
- [ ] `useUnblockUser.ts` — wraps `useMutation` calling `moderationApi.unblockUser(username)`. On success, invalidate the blocked users list query (`['blocked-users']`) and the user profile query. On error, show an error toast.
- [ ] Place all three hooks in `frontend/src/hooks/moderation/` following the `hooks/notification/` sub-folder pattern.

---

### `ReportDialog.tsx`

#### Props interface

- [ ] `open: boolean` — controls whether the dialog is visible.
- [ ] `onClose: () => void` — called when the user cancels or after successful submission.
- [ ] `entityType: ReportEntityType` — the type of content being reported.
- [ ] `entityId: string` — the UUID of the content being reported.
- [ ] `title?: string` — optional context label shown in the dialog subtitle (e.g., `"Report this post"`, `"Report @username"`). If not provided, derive a generic title from `entityType`.

#### Internal state

- [ ] `selectedReason: ReportReason | null` — which radio option is selected; `null` on open.
- [ ] `details: string` — the optional free-text field; empty string on open.

#### Hooks to call inside the component

- [ ] `const { mutate: submitReport, isPending, isError } = useSubmitReport()`.

#### Layout structure

- [ ] Root: MUI `Dialog` with `open={open}` and `onClose={onClose}`.
- [ ] `DialogTitle`: display the context title (from `title` prop or derived from `entityType`). Add a close `IconButton` in the top-right corner (`CloseIcon`) that calls `onClose`.
- [ ] `DialogContent`:
  - A brief instruction paragraph: `"Why are you reporting this?"`.
  - MUI `RadioGroup` listing all `ReportReason` values as individual `FormControlLabel` + `Radio` pairs. Each label should be a human-readable string (not the raw enum key). Define a `REASON_LABELS: Record<ReportReason, string>` constant inside the file: e.g., `SPAM: 'It's spam'`, `HATE_SPEECH: 'Hate speech or symbols'`, `NUDITY: 'Nudity or sexual activity'`, etc. This keeps the label definitions co-located with the component.
  - When `selectedReason === 'OTHER'`: show an additional MUI `TextField` for the `details` field with `multiline`, `rows={3}`, `placeholder="Tell us more (optional)"`, and `inputProps={{ maxLength: 1000 }}`.
- [ ] `DialogActions`:
  - `Button variant="text"` — "Cancel" — calls `onClose`. Disabled while `isPending`.
  - `Button variant="contained"` — "Submit Report" — disabled if `selectedReason === null` or `isPending`. Shows MUI `CircularProgress size={16}` inside the button while `isPending`. On click, calls `submitReport({ entityType, entityId, reason: selectedReason, details: details.trim() || undefined })`. On success, call `onClose` — the mutation's `onSuccess` callback handles the toast.
- [ ] Error state: if `isError`, show a `Typography color="error"` message beneath the `RadioGroup` saying "Something went wrong. Please try again."
- [ ] Reset state on close: use a `useEffect` that watches `open`. When `open` transitions from `true` to `false`, reset `selectedReason` to `null` and `details` to `''`.

#### Where `ReportDialog` is triggered

- [ ] Document (without implementing here) that `ReportDialog` should be wired into:
  - The post kebab menu in `PostCard.tsx` (Phase 2 component) — triggered by a "Report" menu item.
  - The comment kebab menu in `CommentItem.tsx` (Phase 4 component).
  - The user profile "more options" menu in `ProfilePage.tsx` (Phase 1 component).
- [ ] These wiring points are out of scope for this task but must be listed here as follow-up work.

---

### `BlockButton.tsx`

#### Props interface

- [ ] `username: string` — the username of the user to block or unblock.
- [ ] `isBlocked: boolean` — whether the current user has already blocked this user. The parent component (profile kebab menu) is responsible for determining this value and passing it in.
- [ ] `onToggle?: () => void` — optional callback called after a successful block or unblock so the parent can update its own state.

#### Internal state

- [ ] `confirmOpen: boolean` — controls whether the confirmation dialog is shown; `false` by default.

#### Hooks to call inside the component

- [ ] `const { mutate: block, isPending: isBlocking } = useBlockUser()`.
- [ ] `const { mutate: unblock, isPending: isUnblocking } = useUnblockUser()`.
- [ ] `const isPending = isBlocking || isUnblocking`.

#### Layout structure

- [ ] If `isBlocked` is `false` (not yet blocked):
  - Render a MUI `MenuItem` or `Button` labelled `"Block @{username}"` with a `BlockIcon` (from `@mui/icons-material/Block`).
  - `onClick`: set `confirmOpen = true` to open the confirmation dialog.

- [ ] If `isBlocked` is `true` (already blocked):
  - Render a MUI `MenuItem` or `Button` labelled `"Unblock @{username}"`.
  - `onClick`: call `unblock(username)` directly (no confirmation required for unblock — it is a reversible action).
  - Disable the button while `isPending`.

- [ ] Confirmation dialog (for block only):
  - MUI `Dialog` with `open={confirmOpen}` and `onClose={() => setConfirmOpen(false)}`.
  - `DialogTitle`: "Block @{username}?".
  - `DialogContent`: MUI `DialogContentText` explaining the consequences: "They won't be able to see your posts, and you won't see theirs. We won't notify them that you've blocked them."
  - `DialogActions`:
    - `Button variant="text"` — "Cancel" — closes the dialog.
    - `Button variant="contained" color="error"` — "Block" — disabled while `isPending`. On click: call `block(username, { onSuccess: () => { setConfirmOpen(false); onToggle?.(); } })`.

#### Accessibility

- [ ] The block/unblock button must have an `aria-label` attribute set to `"Block @{username}"` or `"Unblock @{username}"` as appropriate, for screen-reader accessibility.

---

## Notes

- `ReportDialog` and `BlockButton` are intentionally separate components. `ReportDialog` is relevant to posts, comments, messages, and users; `BlockButton` is only relevant to user profiles. Combining them would create an awkward props interface.
- `BlockButton` receives `isBlocked` as a prop rather than fetching the status internally. This avoids an extra API call per profile kebab render. The parent (typically the user profile "more options" menu) already has access to the user relationship state.
- The `REASON_LABELS` constant in `ReportDialog` is defined inside the component file (not exported). If another component needs the same labels, extract it to a shared constants file at that point — do not extract prematurely.
- Both components must handle loading states gracefully — disable the primary action button while `isPending` to prevent double submissions.
