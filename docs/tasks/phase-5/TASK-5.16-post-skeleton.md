# TASK-5.16 — Post Skeleton: PostSkeleton.tsx

## Overview

Create a `PostSkeleton` component that mimics the visual structure of a `PostCard` using MUI `Skeleton` components. It is shown while the first page of feed data is loading.

## Requirements

- Lives in `frontend/src/components/posts/`.
- Uses MUI `Skeleton` — no real data, no API calls.
- Matches the visual layout of `PostCard`: avatar + username line, image area, caption lines, action buttons.
- Exported as a named export. Also export `PostSkeletonList` for rendering N skeletons at once.

## File Location

```
frontend/src/components/posts/PostSkeleton.tsx
```

---

## Checklist

### Single skeleton

- [ ] Create `PostSkeleton` component:

  ```tsx
  import { Avatar, Box, Card, CardContent, CardHeader, Skeleton } from '@mui/material';

  export function PostSkeleton() {
    return (
      <Card sx={{ mb: 2, maxWidth: 614, mx: 'auto' }}>
        {/* Header: avatar circle + two text lines (username + timestamp) */}
        <CardHeader
          avatar={<Skeleton variant="circular"><Avatar /></Skeleton>}
          title={<Skeleton width="40%" height={16} />}
          subheader={<Skeleton width="25%" height={14} />}
        />

        {/* Image area */}
        <Skeleton variant="rectangular" width="100%" height={400} />

        {/* Action buttons row */}
        <CardContent>
          <Box display="flex" gap={1} mb={1}>
            <Skeleton variant="circular" width={28} height={28} />
            <Skeleton variant="circular" width={28} height={28} />
            <Skeleton variant="circular" width={28} height={28} />
          </Box>

          {/* Like count line */}
          <Skeleton width="20%" height={16} sx={{ mb: 0.5 }} />

          {/* Caption: two lines */}
          <Skeleton width="90%" height={14} />
          <Skeleton width="60%" height={14} />
        </CardContent>
      </Card>
    );
  }
  ```

### Skeleton list

- [ ] Export a `PostSkeletonList` component for rendering multiple skeletons:

  ```tsx
  interface PostSkeletonListProps {
    count?: number;
  }

  export function PostSkeletonList({ count = 3 }: PostSkeletonListProps) {
    return (
      <>
        {Array.from({ length: count }).map((_, i) => (
          <PostSkeleton key={i} />
        ))}
      </>
    );
  }
  ```

## Notes

- `maxWidth: 614` matches Instagram's feed column width — adjust to match the actual `PostCard` width in this project.
- `Skeleton variant="circular"` with a child `<Avatar />` is the correct MUI v5 pattern for circular skeletons — the `Avatar` provides the size reference.
- Do not animate with a custom keyframe — MUI `Skeleton` already has a default pulse animation.
