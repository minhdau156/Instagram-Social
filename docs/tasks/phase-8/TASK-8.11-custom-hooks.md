# TASK-8.11 — Custom Hooks: useSearch, useSearchHistory, useHashtagPosts

## Overview

Create three hooks that form the data layer for the search feature. Components call these hooks and never touch `searchApi` directly. Use TanStack React Query v5 throughout — no manual `useEffect` + `useState` for data fetching.

## Requirements

- Use `useQuery`, `useMutation`, and `useInfiniteQuery` from `@tanstack/react-query` v5.
- Query keys follow the project convention: arrays with a descriptive first string.
- `queryClient` is obtained via `useQueryClient()` inside each hook — never imported as a singleton.

## File Locations

```
frontend/src/hooks/search/useSearch.ts
frontend/src/hooks/search/useSearchHistory.ts
frontend/src/hooks/search/useHashtagPosts.ts
```

---

## Instructions

### `useSearch.ts`

**Purpose:** Executes a debounced search query and returns typed results. Used by `SearchBar` (live dropdown as the user types) and `SearchPage` (full page results when the user submits).

**Props/parameters the hook accepts:**
- `q: string` — the search term
- `type: SearchType` — `'users'`, `'hashtags'`, or `'posts'`
- `page?: number` — defaults to `0`
- `size?: number` — defaults to `20`

**Debounce:**
React Query itself does not debounce. You must debounce `q` locally inside the hook using `useState` + `useEffect` before passing it to `useQuery`. Here is the pattern:
1. Declare `const [debouncedQ, setDebouncedQ] = useState(q)`.
2. In a `useEffect` that watches `q`, set a `setTimeout` with 300 ms delay that calls `setDebouncedQ(q)`.
3. Return a cleanup function from the effect that calls `clearTimeout(timer)`. This ensures that if `q` changes again before the 300 ms elapses, the previous timeout is cancelled and only the latest value is applied.
4. Pass `debouncedQ` (not the raw `q`) to the `queryFn`.

**Why debounce matters:** Without debouncing, every keystroke fires an API request. A user typing `"travel"` would fire 6 requests (`"t"`, `"tr"`, `"tra"`, etc.). Debouncing waits 300 ms of inactivity before firing, reducing network load and flicker.

**Query configuration:**
- `queryKey`: `['search', debouncedQ, type, page]` — include all variables that affect the result so React Query can cache each combination independently.
- `queryFn`: call the appropriate `searchApi` function based on `type`:
  - `'users'` → `searchApi.searchUsers(debouncedQ, page, size)`
  - `'hashtags'` → `searchApi.searchHashtags(debouncedQ, page, size)`
  - `'posts'` → `searchApi.searchPosts(debouncedQ, page, size)`
- `enabled`: set to `debouncedQ.trim().length >= 1`. This prevents the query from firing when the input is empty or whitespace-only. React Query will return `{ data: undefined, isLoading: false }` while `enabled` is false.
- `staleTime`: set to `30_000` (30 seconds). Search results do not need to be refetched on every window focus — a 30-second cache window is sufficient.

**Return from the hook:**
- `results` — the typed array. Because `type` determines the shape, the hook must return a union type. Use overload signatures or return `data ?? []` typed as `UserSearchResult[] | HashtagSearchResult[] | PostSearchResult[]`. The component narrows the type via the `type` prop it already has.
- `isLoading` — true while the first fetch is in progress
- `isFetching` — true any time a fetch is in progress (including refetches); use this to show a subtle loading indicator in the search bar dropdown without a full skeleton
- `isError`

---

### `useSearchHistory.ts`

**Purpose:** Fetches the user's recent search history and provides a mutation to clear it. Used by `SearchBar` (shows history when input is focused and empty) and `RecentSearches` component.

**Fetching — use `useQuery`:**
- `queryKey`: `['search-history']`
- `queryFn`: calls `searchApi.getSearchHistory()`
- `staleTime`: `60_000` (1 minute) — history does not need real-time freshness.

**Clear mutation — use `useMutation`:**
- `mutationFn`: calls `searchApi.clearSearchHistory()`
- `onSuccess`: call `queryClient.invalidateQueries({ queryKey: ['search-history'] })` so the list empties immediately after the user clicks "Clear all".

**Return from the hook:**
- `history` — `SearchHistoryItem[]`, defaults to `[]` when data is not yet loaded (`data ?? []`)
- `isLoading`
- `clearHistory()` — function that calls the mutation
- `isClearing` — `true` while the clear mutation is in-flight (use to disable the "Clear all" button to prevent double-clicks)

---

### `useHashtagPosts.ts`

**Purpose:** Infinite-scroll list of posts for a given hashtag page. Used by `HashtagPage`.

**Parameters the hook accepts:**
- `hashtagName: string` — the hashtag without `#`

**Fetching — use `useInfiniteQuery`:**
`useInfiniteQuery` is the correct hook for "load more" pagination. Each call fetches one page of posts; React Query accumulates all pages internally.

- `queryKey`: `['hashtag-posts', hashtagName]` — keyed on the hashtag name so different hashtag pages are cached independently.
- `queryFn`: calls `searchApi.getPostsByHashtag(hashtagName, pageParam, 20)`. `pageParam` starts at `0` and is provided by React Query.
- `initialPageParam`: `0`
- `getNextPageParam`: receives the last fetched page (an array of `PostSearchResult`) and the array of all pages fetched so far. If the last page had exactly 20 items, assume there may be more and return the total number of pages fetched as the next page number. If fewer than 20 items came back, return `undefined` — this signals React Query that there are no more pages and sets `hasNextPage` to `false`.
- `enabled`: set to `hashtagName.length > 0` to prevent queries with an empty hashtag name.

**Flattening pages:**
`useInfiniteQuery` stores data as `{ pages: PostSearchResult[][] }`. Before returning, flatten it: `data?.pages.flat() ?? []`.

**Return from the hook:**
- `posts` — the flattened `PostSearchResult[]`
- `isLoading` — true on the first fetch
- `isFetchingNextPage` — true while additional pages are being fetched (show a bottom spinner)
- `fetchNextPage` — call this when the scroll sentinel enters the viewport
- `hasNextPage` — false when `getNextPageParam` returned `undefined`
- `isError`

## Notes

- All three hooks must be placed in `frontend/src/hooks/search/` — following the same sub-folder pattern used for `hooks/notification/` in Phase 7.
- `useSearch` deliberately does NOT use `useInfiniteQuery` — the search dropdown shows a fixed set of results (top 10), not an infinite list. `SearchPage` uses standard `page` pagination. Only `HashtagPage` needs infinite scroll.
- The debounce `useEffect` in `useSearch` must have `[q]` in its dependency array and return the `clearTimeout` cleanup. Missing the cleanup causes memory leaks and stale state bugs.
