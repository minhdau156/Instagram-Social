# TASK-5.15 — Explore Page: ExplorePage.tsx

## Overview

Create `ExplorePage.tsx` — the discover/explore page. Shows trending hashtag chips at the top and a masonry image grid of posts from non-followed users below. Clicking a post opens the `PostDetailModal`.

## Requirements

- Lives in `frontend/src/pages/explore/`.
- Protected route — redirect to `/login` if unauthenticated.
- Uses `useExploreFeed` hook and the `InfiniteScroll` wrapper.
- Trending hashtags: chip row using `getTrendingHashtags`.
- Post grid: MUI `ImageList` with `variant="masonry"` (2 columns mobile, 3 columns desktop).
- Clicking a post opens a modal (reuses `PostDetailModal` from Phase 4 if available, otherwise links to `/posts/:id`).

## File Location

```
frontend/src/pages/explore/ExplorePage.tsx
```

---

## Checklist

### Step 1 — Trending hashtags section

- [ ] Fetch trending hashtags with `useQuery`:

  ```tsx
  const { data: hashtags = [] } = useQuery({
    queryKey: ['trendingHashtags'],
    queryFn: () => getTrendingHashtags(10),
    staleTime: 5 * 60_000, // hashtag trends change slowly — cache 5 min
  });
  ```

- [ ] Render as a horizontally-scrollable chip row:

  ```tsx
  <Box
    display="flex"
    gap={1}
    sx={{ overflowX: 'auto', pb: 1, mb: 3 }}
  >
    {hashtags.map((tag) => (
      <Chip
        key={tag.id}
        label={`#${tag.name}`}
        onClick={() => {/* TODO: navigate to hashtag search in Phase 8 */}}
        sx={{ flexShrink: 0 }}
      />
    ))}
  </Box>
  ```

  > If `hashtags` is empty (before the first nightly rollup), hide the section entirely with `{hashtags.length > 0 && <Box ...>}`.

### Step 2 — Explore feed with masonry grid

- [ ] Set up data fetching:

  ```tsx
  const {
    data,
    isLoading,
    isError,
    refetch,
    fetchNextPage,
    hasNextPage,
    isFetchingNextPage,
  } = useExploreFeed();

  const posts = data?.pages.flatMap((page) => page.posts) ?? [];
  ```

- [ ] Loading state — show a 3-column skeleton grid:

  ```tsx
  {isLoading && (
    <ImageList variant="masonry" cols={3} gap={4}>
      {Array.from({ length: 9 }).map((_, i) => (
        <ImageListItem key={i}>
          <Skeleton
            variant="rectangular"
            width="100%"
            height={i % 3 === 0 ? 300 : 200}
          />
        </ImageListItem>
      ))}
    </ImageList>
  )}
  ```

- [ ] Error state:

  ```tsx
  {isError && (
    <Alert
      severity="error"
      action={<Button size="small" onClick={() => refetch()}>Retry</Button>}
    >
      Could not load explore feed.
    </Alert>
  )}
  ```

- [ ] Empty state (no posts returned):

  ```tsx
  {!isLoading && !isError && posts.length === 0 && (
    <Box textAlign="center" py={8}>
      <ExploreOutlinedIcon sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />
      <Typography variant="h6">Nothing to explore yet</Typography>
      <Typography variant="body2" color="text.secondary">
        Follow more people to personalise your explore feed.
      </Typography>
    </Box>
  )}
  ```

- [ ] Masonry grid content wrapped in `InfiniteScroll`:

  ```tsx
  {posts.length > 0 && (
    <InfiniteScroll
      fetchNextPage={fetchNextPage}
      hasNextPage={hasNextPage}
      isFetchingNextPage={isFetchingNextPage}
    >
      <ImageList
        variant="masonry"
        cols={3}
        gap={4}
        sx={{ columnCount: { xs: 2, sm: 3 } }}
      >
        {posts.map((post) => (
          <ImageListItem
            key={post.id}
            sx={{ cursor: 'pointer' }}
            onClick={() => setSelectedPost(post)}
          >
            <img
              src={post.mediaItems[0]?.mediaUrl}
              alt={post.caption ?? ''}
              loading="lazy"
              style={{ display: 'block', width: '100%' }}
            />
          </ImageListItem>
        ))}
      </ImageList>
    </InfiniteScroll>
  )}
  ```

### Step 3 — Post detail modal

- [ ] Add local state for the selected post:

  ```tsx
  const [selectedPost, setSelectedPost] = useState<Post | null>(null);
  ```

- [ ] Render the modal below the grid:

  ```tsx
  {selectedPost && (
    <PostDetailModal
      post={selectedPost}
      open={!!selectedPost}
      onClose={() => setSelectedPost(null)}
    />
  )}
  ```

  > **If `PostDetailModal` does not exist yet:** use `<Dialog>` with a simple `PostCard` inside as a placeholder. Do not block this task on creating the modal — note it as a TODO.

### Step 4 — Guard and full component shape

- [ ] Full component outline:

  ```tsx
  import { useState } from 'react';
  import { Navigate } from 'react-router-dom';
  import { useQuery } from '@tanstack/react-query';
  import { Alert, Box, Button, Chip, Container, ImageList, ImageListItem,
           Skeleton, Typography } from '@mui/material';
  import ExploreOutlinedIcon from '@mui/icons-material/ExploreOutlined';
  import { useAuth } from '../../hooks/useAuth';
  import { useExploreFeed } from '../../hooks/useExploreFeed';
  import { getTrendingHashtags } from '../../api/feedApi';
  import { InfiniteScroll } from '../../components/common/InfiniteScroll';
  import type { Post } from '../../types/post';
  import type { TrendingHashtag } from '../../types/hashtag';

  export default function ExplorePage() {
    const { user } = useAuth();
    const [selectedPost, setSelectedPost] = useState<Post | null>(null);

    // Always call hooks before early returns
    const { data: hashtags = [] } = useQuery({ /* ... */ });
    const { data, isLoading, isError, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } =
      useExploreFeed();

    if (!user) return <Navigate to="/login" replace />;

    const posts = data?.pages.flatMap((page) => page.posts) ?? [];

    return (
      <Container maxWidth="md" sx={{ py: 3 }}>
        {/* Hashtag chips */}
        {/* ... */}

        {/* Feed grid */}
        {/* ... */}

        {/* Modal */}
        {/* ... */}
      </Container>
    );
  }
  ```

## Notes

- `ImageList variant="masonry"` requires the browser to support CSS `column-count` — it works in all modern browsers. The items do not need equal heights; masonry distributes them automatically.
- `loading="lazy"` on `<img>` defers image loading until the image is near the viewport — important for a grid with many images.
- The `columnCount` sx override (`{ xs: 2, sm: 3 }`) reduces to 2 columns on mobile for readability. MUI's `cols` prop does not respond to breakpoints by default, hence the manual override.
- `ExploreOutlinedIcon` is from `@mui/icons-material` — confirm it is installed. If not, use any other suitable icon from the same package.
