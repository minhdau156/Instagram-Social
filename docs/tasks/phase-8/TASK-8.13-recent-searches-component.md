# TASK-8.13 — Recent Searches Component: RecentSearches

## Overview

Create the `RecentSearches` component — a list of the user's previous search queries shown inside the `SearchBar` dropdown when the input is focused and empty. Each item can be clicked to re-run the search, and individual items or all items can be removed.

## Requirements

- Lives in `frontend/src/components/search/`.
- MUI components and `sx` prop only.
- Fully typed — no `any`.
- Named export.
- Receives no props from `SearchBar` — it calls `useSearchHistory()` internally.

## File Location

```
frontend/src/components/search/RecentSearches.tsx
```

---

## Instructions

### Props interface

```ts
interface RecentSearchesProps {
  onSelect: (query: string) => void;
  // Called when the user clicks a history item.
  // The parent (SearchBar) uses this to populate the input and trigger a search.
}
```

### Hooks to call inside the component

- `const { history, clearHistory, isClearing } = useSearchHistory()`
- `const navigate = useNavigate()` from `react-router-dom`

### Layout structure

**Root container:**
- MUI `Box` with `py: 1`.

**Header row** (shown only when `history.length > 0`):
- MUI `Box` with `display: 'flex'`, `justifyContent: 'space-between'`, `alignItems: 'center'`, `px: 2`, `pb: 1`.
- Left side: `Typography variant="subtitle2" color="text.secondary"` — `"Recent"`.
- Right side: MUI `Button variant="text" size="small" color="primary"` — `"Clear all"`.
  - `onClick`: calls `clearHistory()`.
  - `disabled={isClearing}` — disables the button while the delete request is in-flight to prevent double-clicks.

**Empty state** (shown when `history.length === 0`):
- MUI `Box` with `py: 2`, `textAlign: 'center'`.
- `Typography color="text.secondary" variant="body2"` — `"No recent searches"`.

**History list** (shown when `history.length > 0`):
- MUI `List disablePadding`.
- Map over `history` (which is already capped at 10 entries by the backend). Each item:

  **`ListItemButton`:**
  - `onClick`: call `onSelect(item.query)`. The parent (`SearchBar`) receives the query string, populates the input, and triggers a search. The component itself does NOT navigate — it delegates to the parent.
  - No `sx` background highlight needed (these are not selectable for keyboard nav in this component).

  **`ListItemAvatar`:**
  - MUI `Avatar` with `sx={{ width: 36, height: 36, bgcolor: 'action.hover' }}`.
  - Inside the Avatar: a `HistoryIcon` (from `@mui/icons-material/History`) with `fontSize="small"` and `color="text.secondary"`.
  - Why an Avatar here: it makes the layout visually consistent with user/hashtag search results that appear in the same dropdown, creating a uniform list appearance.

  **`ListItemText`:**
  - `primary={item.query}` — the search term.
  - `secondary`: a relative timestamp. Import `formatDistanceToNow` from `date-fns` and call it with `new Date(item.searchedAt)` and `{ addSuffix: true }` to get strings like `"2 hours ago"`. Wrap in a `try/catch` in case `searchedAt` is malformed.
  - `primaryTypographyProps`: `{ variant: 'body2', noWrap: true }` — truncate long queries with an ellipsis.

  **Secondary action (`ListItemSecondaryAction`):**
  - MUI `IconButton size="small"` with `CloseIcon` (from `@mui/icons-material/Close`).
  - `onClick`: stop event propagation (`e.stopPropagation()`) so the `ListItemButton`'s own `onClick` does not also fire. Then call a `removeItem(item.id)` function.
  - **`removeItem` function:** `useSearchHistory` does not expose a per-item remove mutation (the backend `DELETE /search/history` clears all). For individual removal, two options exist:
    1. **Optimistic local filter only (recommended for now):** Keep a local `Set<string>` of hidden IDs in component state. When the X button is clicked, add `item.id` to the set and filter the displayed list. This gives instant feedback without a backend call. The history is fully cleared on next "Clear all" click.
    2. **Add a per-item delete endpoint** to the backend and a corresponding API function — this is out of scope for Phase 8 but can be noted as a future improvement.
  - Use option 1 (local filter). Declare `const [hiddenIds, setHiddenIds] = useState<Set<string>>(new Set())`. Filter `history` before mapping: `history.filter(item => !hiddenIds.has(item.id))`.

### Divider between items

- Add `<Divider component="li" />` between list items to visually separate entries. Use the `component="li"` prop so the divider is valid HTML inside a `<ul>` (MUI `List` renders as `<ul>` by default).

## Notes

- `RecentSearches` is only rendered inside `SearchBar` — it does not appear on any route by itself.
- The `HistoryIcon` gives a visual cue that these are past searches, distinguishing them from live search results (which show an avatar or a tag chip).
- The `formatDistanceToNow` import requires `date-fns`. It was already added as a dependency in Phase 7 (TASK-7.18). Verify it is in `package.json` before importing.
- `e.stopPropagation()` on the X button is critical — without it, clicking X would simultaneously fire the `ListItemButton`'s `onClick`, causing the query to be re-run right as it is being removed.
- `primaryTypographyProps={{ noWrap: true }}` prevents long queries from wrapping to multiple lines inside the dropdown.
