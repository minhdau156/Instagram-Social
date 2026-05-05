# TASK-4.29 — Like Components: LikeButton & LikersTooltip

## Overview

Create the reusable like UI components for the post feed and post detail view. These components handle the visual states of like interactions and are used inside `PostCard` and `PostDetailModal`.

## Requirements

- Lives in `frontend/src/components/posts/`.
- Uses Material UI (MUI) v5 components and `@mui/icons-material`.
- Each component is focused and reusable — no page-level logic inside.
- Animated heart icon for engaging UX.
- Loading state handled gracefully.

## File Locations

```
frontend/src/components/posts/
├── LikeButton.tsx
└── LikersTooltip.tsx
```

---

## Checklist

### `LikeButton.tsx`

- [x] Props interface:
  ```typescript
  interface LikeButtonProps {
    postId: string;
    liked: boolean;           // current liked state from post data
    likeCount: number;        // current count from post data
    disabled?: boolean;       // disable when not authenticated
  }
  ```

- [x] Uses `useLikePost(postId)` hook

- [x] Renders MUI `IconButton` with:
  - `FavoriteIcon` (filled, color `error`) when liked
  - `FavoriteBorderIcon` (outline, color `inherit`) when not liked

- [x] Add CSS animation on the heart icon when liked state changes:
  ```css
  @keyframes heartPop {
    0%   { transform: scale(1); }
    50%  { transform: scale(1.35); }
    100% { transform: scale(1); }
  }
  ```
  Apply with an MUI `sx` prop or a CSS module when `liked` transitions from `false` → `true`.

- [x] Show like count as `Typography` next to the icon button

- [x] Show `CircularProgress` (size 16) inside the button while mutation is pending

- [x] On click: call `likeMutation.mutate(liked)` (passes current liked state; hook decides like vs unlike)

- [x] Example structure:
  ```tsx
  export function LikeButton({ postId, liked, likeCount, disabled }: LikeButtonProps) {
    const likeMutation = useLikePost(postId);
    const [animating, setAnimating] = useState(false);

    const handleClick = () => {
      if (disabled) return;
      if (!liked) setAnimating(true);
      likeMutation.mutate(liked);
    };

    return (
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
        <IconButton
          onClick={handleClick}
          disabled={likeMutation.isPending}
          size="small"
          aria-label={liked ? 'Unlike post' : 'Like post'}
        >
          {likeMutation.isPending ? (
            <CircularProgress size={16} />
          ) : liked ? (
            <FavoriteIcon color="error" sx={animating ? { animation: 'heartPop 0.3s ease' } : {}} />
          ) : (
            <FavoriteBorderIcon />
          )}
        </IconButton>
        <Typography variant="body2">{likeCount}</Typography>
      </Box>
    );
  }
  ```

- [x] Use `useEffect` to reset `animating` state after animation completes (300ms)
- [x] Add `aria-label` on the icon button for accessibility

---

### `LikersTooltip.tsx`

- [x] Props interface:
  ```typescript
  interface LikersTooltipProps {
    postId: string;
    likeCount: number;
    previewUsernames?: string[];  // first 2-3 likers from post data (pre-fetched)
  }
  ```

- [x] Renders an MUI `Tooltip` wrapping a `Typography` link:
  - When `likeCount === 0`: render nothing (or a "Be the first to like" text)
  - When `likeCount === 1`: `"Liked by **username1**"`
  - When `likeCount === 2`: `"Liked by **username1** and **username2**"`
  - When `likeCount > 2`: `"Liked by **username1**, **username2** and **N others**"`

- [x] On click: open `LikersDialog` (a separate dialog that fetches full likers list)

- [x] `LikersDialog` sub-component (can live in the same file or `LikersDialog.tsx`):
  - MUI `Dialog` with title `"Likes"`
  - On open: fetch `getPostLikers(postId)` using a `useQuery`
  - `List` of `UserListItem` (imported from follow components)
  - Loading spinner while fetching
  - Empty state if `likeCount === 0`
