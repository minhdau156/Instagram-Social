# TASK-8.14 — Pages: SearchPage, HashtagPage

## Overview

Create two route-level pages. `SearchPage` displays full paginated search results across three tabs (People, Hashtags, Posts). `HashtagPage` displays all posts for a given hashtag in an infinite-scroll grid. Both are protected routes.

## Requirements

- Pages live in `frontend/src/pages/search/`.
- Both must use `export default function` — required for `React.lazy` compatibility (TASK-8.15).
- MUI and `sx` prop only for all styling.
- Read URL query parameters (`?q=...&type=...`) and path parameters (`:name`) from the router — do not manage them only in local state.

## File Locations

```
frontend/src/pages/search/SearchPage.tsx
frontend/src/pages/search/HashtagPage.tsx
```

---

## Instructions

### `SearchPage.tsx`

**Purpose:** Full-page search results with tabs for People, Hashtags, and Posts.

**Reading URL parameters:**
Use `useSearchParams()` from `react-router-dom` to read `q` and `type` from the URL. This is important because the `SearchBar` navigates to `/search?q={query}&type=users` — the page must derive its state from the URL, not from props, so bookmarks and browser back/forward work correctly.

```ts
const [searchParams, setSearchParams] = useSearchParams();
const q = searchParams.get('q') ?? '';
const activeTab = (searchParams.get('type') ?? 'users') as SearchType;
```

**Tabs:**
Use MUI `Tabs` + `Tab` components to switch between types. The current tab is derived from `activeTab` (the `type` URL param), not from a `useState`. When the user clicks a tab, update the URL param:

```ts
const handleTabChange = (_: React.SyntheticEvent, newType: SearchType) => {
  setSearchParams({ q, type: newType });
};
```

**Why update the URL instead of `useState`:** If you used `useState` for the active tab, the URL would stay at `?type=users` even after switching to "Hashtags". Keeping tab state in the URL allows deep-linking, browser back/forward, and sharing search result links.

Tab labels:
- `value="users"` → label `"People"`
- `value="hashtags"` → label `"Hashtags"`
- `value="posts"` → label `"Posts"`

**Data:**
Call `useSearch(q, activeTab, page, size)` passing the values derived from the URL. Use `page` from a local `useState(0)` that resets to `0` whenever `q` or `activeTab` changes (use a `useEffect` that watches both).

**Page layout:**
- Root: MUI `Box` with `maxWidth: 700`, `mx: 'auto'`, `py: 2`, responsive `px: { xs: 1, sm: 2 }`.
- Title row: `Typography variant="h5" fontWeight={600} mb={2}` — `"Search"`. If `q` is not empty, append `' results for "{q}"'` to the title.
- MUI `Tabs` below the title, `value={activeTab}`, `onChange={handleTabChange}`, `indicatorColor="primary"`, `textColor="primary"`. Add a `Divider` below the Tabs.
- Result list below the Divider.

**Loading state:** While `isLoading` is true, show 5 skeleton items appropriate to the active tab:
- People tab skeleton: `Skeleton variant="circular" width={40} height={40}` inline with `Skeleton variant="text" width={120}` — mimics an avatar + username row.
- Hashtags tab skeleton: `Skeleton variant="rectangular" height={40}` for a chip-like shape.
- Posts tab skeleton: 3-column grid of `Skeleton variant="rectangular"` squares (mimics the post grid).

**Empty state:** When not loading, `q` is not empty, and `results.length === 0`:
- MUI `Box` with `py: 8`, `textAlign: 'center'`.
- `Typography color="text.secondary"` — `'No results found for "${q}"'`.

**Empty query state:** When `q` is empty:
- Show `RecentSearches` component with `onSelect={(query) => setSearchParams({ q: query, type: activeTab })}` — clicking a history item updates the URL params and triggers a search.

**Rendering results — People tab (`type === 'users'`):**
- MUI `List`.
- Each `UserSearchResult` as a `ListItem`:
  - `ListItemAvatar`: `Avatar src={user.avatarUrl ?? undefined}` with the first letter of `user.username` as fallback text.
  - `ListItemText primary={user.username} secondary={user.fullName}`.
  - Secondary action: `FollowButton` component from Phase 3 (if it accepts a `userId` prop and works standalone). If not suitable, use a plain MUI `Button variant="outlined" size="small"` — `"Follow"` — that navigates to `/profile/{user.username}`.
  - The entire row (`ListItemButton`) navigates to `/profile/{user.username}` on click.

**Rendering results — Hashtags tab (`type === 'hashtags'`):**
- MUI `List`.
- Each `HashtagSearchResult` as a `ListItemButton` navigating to `/hashtag/{hashtag.name}`:
  - `ListItemAvatar`: `Avatar sx={{ bgcolor: 'primary.main' }}` with a `TagIcon` inside.
  - `ListItemText primary={'#' + hashtag.name} secondary={hashtag.postCount.toLocaleString() + ' posts'}`.

**Rendering results — Posts tab (`type === 'posts'`):**
- MUI `Box` with CSS grid: `display: 'grid'`, `gridTemplateColumns: 'repeat(3, 1fr)'`, `gap: 0.5`.
- Each `PostSearchResult` as a `Box` with `position: 'relative'`, `paddingBottom: '100%'` (square aspect ratio via padding trick), `overflow: 'hidden'`, `cursor: 'pointer'`.
  - Inside: `<img src={post.mediaUrl} alt={post.caption ?? ''}` with `position: 'absolute'`, `width: '100%'`, `height: '100%'`, `objectFit: 'cover'`.
  - On click: navigate to `/posts/{post.id}`.
  - If `post.mediaType === 'VIDEO'`: overlay a small `PlayArrowIcon` in the top-right corner to indicate it is a video.

**Pagination:**
Below the results list/grid, show a MUI `Pagination` component:
- `count`: there is no total count from this API. Use a simple "load more" approach instead: show a `Button variant="outlined"` — `"Load more"` — that increments `page` by 1. When fewer results than `size` are returned, hide the button (it means we've reached the end).
- Alternatively, if the hook is refactored to `useInfiniteQuery`, use an `IntersectionObserver` sentinel (same as `HashtagPage`). For the initial implementation, the "Load more" button is simpler.

---

### `HashtagPage.tsx`

**Purpose:** Displays all posts tagged with a specific hashtag in an infinite-scroll grid.

**Reading URL parameters:**
Use `useParams<{ name: string }>()` from `react-router-dom` to read the hashtag name from the path (e.g., `/hashtag/travel` → `name = "travel"`).

```ts
const { name } = useParams<{ name: string }>();
```

**Data:**
Call `useHashtagPosts(name ?? '')` to get `posts`, `isLoading`, `isFetchingNextPage`, `fetchNextPage`, `hasNextPage`.

**Page layout:**
- Root: MUI `Box` with `maxWidth: 900`, `mx: 'auto'`, `py: 2`, responsive `px`.
- Header section:
  - MUI `Box` with `display: 'flex'`, `alignItems: 'center'`, `mb: 3`, `gap: 2`.
  - Left: `Avatar sx={{ width: 80, height: 80, bgcolor: 'primary.main' }}` containing `TagIcon` with `fontSize="large"`.
  - Right: a `Box` with two Typography lines:
    - `Typography variant="h4" fontWeight={700}` — `'#' + name`.
    - `Typography color="text.secondary"` — total post count. Since the API returns paginated results (not a count), show `posts.length + ' posts loaded'` for now, or call a separate `searchApi.searchHashtags(name ?? '', 0, 1)` in a one-off `useQuery` to get the `postCount` from the first hashtag result. Use the second approach if the visual polish is important.
- MUI `Divider mb={2}`.

**Posts grid:**
- MUI `Box` with CSS grid: `display: 'grid'`, `gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)' }`, `gap: 0.5`. Two columns on mobile, three on tablet and above.
- Each `PostSearchResult` rendered as a square tile:
  - `Box position="relative" paddingBottom="100%" overflow="hidden" cursor="pointer"`.
  - `<img>` with `position: 'absolute'`, `width: '100%'`, `height: '100%'`, `objectFit: 'cover'`, `loading="lazy"` (native browser lazy loading for off-screen images).
  - On click: navigate to `/posts/{post.id}`.
  - Video indicator: if `post.mediaType === 'VIDEO'`, overlay `PlayArrowIcon` top-right.
  - Like/comment counts overlay: on hover, show a semi-transparent dark overlay with `FavoriteIcon + likeCount` and `ChatBubbleIcon + commentCount`. Use MUI `Box` with `position: 'absolute'`, `inset: 0`, `bgcolor: 'rgba(0,0,0,0.35)'`, `display: 'flex'`, `alignItems: 'center'`, `justifyContent: 'center'`, `gap: 2`, `opacity: 0`, and CSS `'&:hover': { opacity: 1 }` via `sx` to toggle the overlay on hover.

**Loading skeleton:**
While `isLoading` is true, render 9 skeleton squares in the grid: `Skeleton variant="rectangular" sx={{ paddingBottom: '100%' }}`.

**Empty state:**
When not loading and `posts.length === 0`:
- `Box py={8} textAlign="center"`.
- `Typography color="text.secondary"` — `'No posts found for #' + name`.

**Infinite scroll sentinel:**
Place an invisible `div ref={sentinelRef}` immediately after the grid. Use `IntersectionObserver` inside a `useEffect` to watch the sentinel — when it enters the viewport, call `fetchNextPage()` only if `hasNextPage && !isFetchingNextPage`. Include `hasNextPage`, `isFetchingNextPage`, and `fetchNextPage` in the effect's dependency array. Return `observer.disconnect()` as the cleanup function.

Below the sentinel, render `CircularProgress size={32} sx={{ display: 'block', mx: 'auto', my: 2 }}` while `isFetchingNextPage` is true.

## Notes

- Both pages use `export default function` — confirm this before registering routes in TASK-8.15.
- `useParams` returns `string | undefined` — always provide a fallback (`name ?? ''`) before passing to the hook.
- The square-tile padding trick (`paddingBottom: '100%'`) makes the tile's height equal to its width regardless of the grid column width. The inner `position: 'absolute'` image then fills the tile completely.
- `loading="lazy"` on `<img>` is a native HTML attribute — no extra library needed. It defers loading images that are below the fold, which is critical for a post grid that can have hundreds of items.
- The hover overlay (`&:hover`: `opacity: 1`) uses MUI's nested `sx` selector syntax, which Emotion compiles to a CSS pseudo-class.
