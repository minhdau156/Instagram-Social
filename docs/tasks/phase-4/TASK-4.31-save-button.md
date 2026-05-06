# TASK-4.31 — Save Button Component

## Overview

Create the `SaveButton` (bookmark) component for posts. It displays a bookmark icon that fills when the post is saved, with an optimistic update via `useSavePost`.

## Requirements

- Lives in `frontend/src/components/posts/`.
- Uses Material UI (MUI) v5 `IconButton` and `@mui/icons-material`.
- Optimistic update via `useSavePost` (already implemented in TASK-4.28).
- Accessible with proper `aria-label`.

## File Location

```
frontend/src/components/posts/SaveButton.tsx
```

---

## Checklist

- [x] Props interface:
  ```typescript
  interface SaveButtonProps {
    postId: string;
    saved: boolean;        // current saved state from post data
    disabled?: boolean;    // disable when not authenticated
  }
  ```

- [x] Uses `useSavePost(postId)` hook

- [x] Renders MUI `IconButton` with:
  - `BookmarkIcon` (filled) when `saved === true`
  - `BookmarkBorderIcon` (outline) when `saved === false`
  - Icon color: `primary` when saved, `inherit` when not saved

- [x] Show `CircularProgress` (size 16) inside the button while mutation is pending

- [x] On click: call `saveMutation.mutate(saved)` (passes current saved state; hook decides save vs unsave)

- [x] Disable button when `disabled === true` (unauthenticated users) or `saveMutation.isPending`

- [x] Add `aria-label`:
  - `"Save post"` when not saved
  - `"Unsave post"` when saved

- [x] Example structure:
  ```tsx
  export function SaveButton({ postId, saved, disabled }: SaveButtonProps) {
    const saveMutation = useSavePost(postId);

    return (
      <IconButton
        onClick={() => !disabled && saveMutation.mutate(saved)}
        disabled={disabled || saveMutation.isPending}
        aria-label={saved ? 'Unsave post' : 'Save post'}
        size="small"
      >
        {saveMutation.isPending ? (
          <CircularProgress size={16} />
        ) : saved ? (
          <BookmarkIcon color="primary" />
        ) : (
          <BookmarkBorderIcon />
        )}
      </IconButton>
    );
  }
  ```

- [x] Add a subtle scale animation on click (similar to `LikeButton`) — optional but improves UX
