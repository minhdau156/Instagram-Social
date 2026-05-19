# TASK-8.12 — Search Bar Component: SearchBar

## Overview

Create the `SearchBar` component — a controlled text input that shows a live dropdown as the user types and displays recent search history when focused with an empty input. This is an inline component used in the app's side navigation or app bar.

## Requirements

- Lives in `frontend/src/components/search/`.
- MUI components and `sx` prop only — no inline `style={{}}`, no hardcoded hex colours.
- Fully typed — no `any`.
- Named export (not default export) — it is a shared component, not a page.
- Must be accessible: keyboard navigation with arrow keys, Enter to confirm, Escape to close.

## File Location

```
frontend/src/components/search/SearchBar.tsx
```

---

## Instructions

### Props interface

```ts
interface SearchBarProps {
  onNavigate?: () => void; // optional callback — e.g. close a sidebar after navigating
}
```

The component manages its own state (query string, focus, dropdown visibility) internally. No `value` or `onChange` prop is needed from the parent — this is a self-contained search widget.

### Internal state

- `const [query, setQuery] = useState('')` — the current text in the input.
- `const [focused, setFocused] = useState(false)` — whether the input currently has focus. Controls when the dropdown is shown.
- `const [activeIndex, setActiveIndex] = useState(-1)` — which item in the dropdown list is currently highlighted via keyboard navigation. `-1` means none.

### Hooks to call inside the component

- `const { results, isFetching } = useSearch(query, 'users')` — for the default dropdown type. In a real app you might search all three types simultaneously; for this implementation, start with user search in the dropdown. The `SearchPage` handles multi-type results.
- `const { history } = useSearchHistory()` — for the recent searches list shown when the input is focused and empty.
- `const navigate = useNavigate()` from `react-router-dom`.

### What to show in the dropdown

Determine what the dropdown renders based on two conditions:

1. **Input is focused AND query is empty:** show the `RecentSearches` component (see TASK-8.13). Do not call the search API.
2. **Input is focused AND query has at least 1 non-whitespace character:** show search results from `useSearch`. During loading (`isFetching`), show skeleton rows. When results are empty, show a "No results" message.
3. **Input is not focused:** show nothing (close the dropdown).

### Layout structure

**Root container:**
- MUI `Box` with `position: 'relative'` — this establishes a positioning context so the dropdown can be absolutely positioned below the input without affecting surrounding layout.

**Input:**
- MUI `TextField` with:
  - `value={query}`
  - `onChange={e => { setQuery(e.target.value); setActiveIndex(-1); }}` — reset active index on every new character so keyboard navigation starts fresh.
  - `onFocus={() => setFocused(true)}`
  - `onBlur` — use a `setTimeout` with a ~150 ms delay before setting `setFocused(false)`. **Why:** if the user clicks an item in the dropdown, the `onBlur` event fires on the input BEFORE the `onClick` event fires on the list item. Without the delay, `focused` becomes false, the dropdown closes, and the click never registers. The 150 ms gives the click event time to fire first.
  - `onKeyDown` — see keyboard navigation section below.
  - `placeholder`: `"Search"`
  - `size="small"` for compact appearance.
  - `fullWidth`
  - `InputProps`: add a `SearchIcon` as `startAdornment`, and an `IconButton` with `CloseIcon` as `endAdornment` — show the close button only when `query.length > 0`, clearing the input on click.

**Dropdown panel:**
- MUI `Paper` with:
  - `position: 'absolute'`
  - `top: '100%'` — sits directly below the TextField.
  - `left: 0`, `right: 0` — same width as the input.
  - `zIndex: theme.zIndex.modal` — ensures it floats above other content.
  - `mt: 0.5` — small gap between input and dropdown.
  - `maxHeight: 400`, `overflow: 'auto'` — scroll if there are many results.
- Render the Paper only when `focused` is true.

### Dropdown content — search results list

Each result item is a `ListItemButton`:
- For a `UserSearchResult`:
  - `ListItemAvatar`: MUI `Avatar` with `src={user.avatarUrl ?? undefined}`. Show first letter of `user.username` as fallback.
  - `ListItemText primary={user.username} secondary={user.fullName}`
  - On click: `navigate('/profile/' + user.username)` then call `onNavigate?.()`.
- For a `HashtagSearchResult` (if you extend the dropdown to multi-type):
  - `ListItemAvatar`: MUI `Avatar` with a `TagIcon` inside (no image — hashtags have no avatar).
  - `ListItemText primary={'#' + hashtag.name} secondary={hashtag.postCount + ' posts'}`
  - On click: `navigate('/hashtag/' + hashtag.name)` then call `onNavigate?.()`.

Highlight the `activeIndex` item: set `sx={{ bgcolor: 'action.selected' }}` on the `ListItemButton` when its index equals `activeIndex`.

### Keyboard navigation

Handle `onKeyDown` on the `TextField`:

- `ArrowDown`: increment `activeIndex` (cap at `results.length - 1`). Prevent default page scroll with `e.preventDefault()`.
- `ArrowUp`: decrement `activeIndex` (floor at `0`). Prevent default.
- `Enter`:
  - If `activeIndex >= 0`: treat it as clicking the highlighted item — navigate to the appropriate route.
  - If `activeIndex === -1` and `query.trim()` is not empty: navigate to `/search?q={query}&type=users` (the full search page).
  - Call `setFocused(false)` and `setQuery('')` after navigating.
- `Escape`: set `setFocused(false)` to close the dropdown without navigating.

### Loading state

While `isFetching` is true (and `query` is not empty), render 3 `Skeleton` items inside the dropdown:
- Each: `Skeleton variant="rectangular" height={48} sx={{ borderRadius: 1, mb: 0.5 }}`.

### Empty state

When `isFetching` is false, `query` is not empty, and `results.length === 0`:
- Render `Typography color="text.secondary" sx={{ p: 2 }}` — `"No results for "{query}""`.

## Notes

- The `onBlur` + `setTimeout` pattern for keeping the dropdown open during mouse clicks is a well-known pattern in search UI — it is intentional, not a hack.
- The `activeIndex` resets to `-1` on every `onChange` so the keyboard highlight always starts fresh after a new keystroke.
- The `RecentSearches` component (TASK-8.13) is rendered inside the dropdown when the input is focused and empty. It is a self-contained component that manages its own data via `useSearchHistory`.
- Do NOT fetch search results when `query.trim().length === 0` — the `enabled` flag in `useSearch` already prevents this, but also avoid calling `useSearch` with an empty string altogether by using a conditional or early return.
- The `onNavigate` prop is optional to allow `SearchBar` to be used both in the persistent sidebar (where navigation stays in-place) and in a modal overlay (where a callback can close the modal after navigation).
