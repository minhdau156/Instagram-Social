# TASK-5.14 — Home Page: HomePage.tsx

## Overview

Create (or replace) `HomePage.tsx` — the main authenticated landing page. It shows an infinite-scroll feed of `PostCard` components in the left column and a "Suggested Users" panel in the right column on desktop.

## Requirements

- Lives in `frontend/src/pages/feed/`.
- Protected route — redirect to `/login` if unauthenticated.
- Uses `useHomeFeed` hook for data.
- Uses the `InfiniteScroll` wrapper from TASK-5.13.
- Uses `PostSkeleton` / `PostSkeletonList` from TASK-5.16 for loading states.
- Responsive: single column on mobile, two-column (feed + sidebar) on desktop (`md` breakpoint and above).

## File Location

```
frontend/src/pages/feed/HomePage.tsx
```

---

## Checklist

### Step 1 — Scaffold the component

- [x] Create `HomePage.tsx`:

  ```tsx
  import { Navigate } from 'react-router-dom';
  import { Alert, Box, Button, Container, Grid, Typography } from '@mui/material';
  import { useAuth } from '../../hooks/useAuth';
  import { useHomeFeed } from '../../hooks/useHomeFeed';
  import { InfiniteScroll } from '../../components/common/InfiniteScroll';
  import { PostCard } from '../../components/posts/PostCard';
  import { PostSkeletonList } from '../../components/posts/PostSkeleton';
  import { SuggestedUsers } from '../../components/users/SuggestedUsers';

  export default function HomePage() {
    const { user } = useAuth();

    // Guard: redirect unauthenticated users
    if (!user) return <Navigate to="/login" replace />;

    const {
      data,
      isLoading,
      isError,
      refetch,
      fetchNextPage,
      hasNextPage,
      isFetchingNextPage,
    } = useHomeFeed();

    // Flatten all loaded pages into a single post array
    const posts = data?.pages.flatMap((page) => page.posts) ?? [];

    return (
      <Container maxWidth="lg" sx={{ py: 3 }}>
        <Grid container spacing={4}>
          {/* ── Left column: feed ── */}
          <Grid item xs={12} md={8}>
            {/* Loading state: show skeletons while first page loads */}
            {isLoading && <PostSkeletonList count={3} />}

            {/* Error state */}
            {isError && (
              <Alert
                severity="error"
                action={
                  <Button size="small" onClick={() => refetch()}>
                    Retry
                  </Button>
                }
              >
                Could not load your feed. Please try again.
              </Alert>
            )}

            {/* Empty state: authenticated but following nobody or no posts yet */}
            {!isLoading && !isError && posts.length === 0 && (
              <Box textAlign="center" py={8}>
                <Typography variant="h6" gutterBottom>
                  Your feed is empty
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  Follow some people to see their posts here.
                </Typography>
              </Box>
            )}

            {/* Feed content with infinite scroll */}
            {posts.length > 0 && (
              <InfiniteScroll
                fetchNextPage={fetchNextPage}
                hasNextPage={hasNextPage}
                isFetchingNextPage={isFetchingNextPage}
              >
                {posts.map((post) => (
                  <PostCard key={post.id} post={post} />
                ))}
              </InfiniteScroll>
            )}
          </Grid>

          {/* ── Right column: suggested users (desktop only) ── */}
          <Grid item md={4} sx={{ display: { xs: 'none', md: 'block' } }}>
            <SuggestedUsers />
          </Grid>
        </Grid>
      </Container>
    );
  }
  ```

### Step 2 — Create `SuggestedUsers` component

- [x] Create `frontend/src/components/users/SuggestedUsers.tsx`:

  ```tsx
  import { useQuery } from '@tanstack/react-query';
  import { Avatar, Box, Button, List, ListItem, ListItemAvatar,
           ListItemText, Skeleton, Typography } from '@mui/material';
  import { usersApi } from '../../api/usersApi';   // adjust import to match actual export

  export function SuggestedUsers() {
    const { data: users, isLoading } = useQuery({
      queryKey: ['suggestedUsers'],
      queryFn: () => usersApi.getSuggestedUsers(5),   // top 5 by follower count
      staleTime: 5 * 60_000,  // refresh every 5 minutes
    });
  ```

  > **Note:** If `usersApi.getSuggestedUsers` does not exist yet, add a stub endpoint call to `GET /api/v1/users/suggested?limit=5`. If the backend hasn't implemented this yet, return an empty array and leave a `// TODO` comment. Do not block `HomePage` on this.

  ```tsx
    if (isLoading) {
      return (
        <Box>
          <Typography variant="subtitle2" sx={{ mb: 1 }}>Suggested for you</Typography>
          {Array.from({ length: 3 }).map((_, i) => (
            <Box key={i} display="flex" alignItems="center" gap={1} mb={1}>
              <Skeleton variant="circular" width={36} height={36} />
              <Skeleton width={120} height={16} />
            </Box>
          ))}
        </Box>
      );
    }

    if (!users || users.length === 0) return null;

    return (
      <Box>
        <Typography variant="subtitle2" color="text.secondary" sx={{ mb: 1 }}>
          Suggested for you
        </Typography>
        <List disablePadding>
          {users.map((user) => (
            <ListItem key={user.id} disablePadding sx={{ mb: 1 }}>
              <ListItemAvatar>
                <Avatar src={user.avatarUrl} sx={{ width: 36, height: 36 }}>
                  {user.username[0].toUpperCase()}
                </Avatar>
              </ListItemAvatar>
              <ListItemText
                primary={user.username}
                primaryTypographyProps={{ variant: 'body2', fontWeight: 600 }}
              />
              <Button size="small" variant="text">
                Follow
              </Button>
            </ListItem>
          ))}
        </List>
      </Box>
    );
  }
  ```

### Step 3 — Pull-to-refresh on mobile (optional enhancement)

- [x] Add swipe-down gesture support using a `touchstart`/`touchend` listener on the feed column:

  ```tsx
  // Simplified version — only trigger refetch if user pulls down > 80px
  // Full implementation: track touchStart Y, compare touchEnd Y, call refetch()
  // This is an optional enhancement — mark as TODO if short on time.
  ```

  > This is low priority. Ship the page without it if it adds complexity.

## Notes

- `PostCard` is an existing component from Phase 4 — do not create a new one. Check `frontend/src/components/posts/PostCard.tsx` for the exact import path.
- `useAuth()` returns `{ user }` where `user` is `null` when not logged in. The `<Navigate>` guard must come before any hooks that depend on `user` — actually in React, hooks must be called before early returns. Move the guard logic into a conditional render rather than an early return if necessary.

  **Correct pattern (hooks before returns):**
  ```tsx
  export default function HomePage() {
    const { user } = useAuth();
    const feedResult = useHomeFeed(); // hooks always called

    if (!user) return <Navigate to="/login" replace />;
    // ... rest of render
  }
  ```
- The `Grid item md={4}` sidebar is hidden on mobile with `display: { xs: 'none', md: 'block' }` — MUI's responsive sx syntax.
