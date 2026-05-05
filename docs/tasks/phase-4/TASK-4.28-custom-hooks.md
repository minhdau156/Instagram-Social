# TASK-4.28 — Custom Hooks: Like, Comment, Save

## Overview

Create React Query custom hooks that encapsulate all data fetching and mutation logic for the Like, Comment, and Save features. These hooks are consumed by components and are the single place where cache key naming and invalidation logic lives.

## Requirements

- Lives in `frontend/src/hooks/`.
- Uses `@tanstack/react-query` v5 (`useMutation`, `useInfiniteQuery`).
- Follow the naming and structure of existing hooks (e.g., `useFollow.ts`, `useFollowers.ts`).
- Optimistic updates for like and save mutations to make the UI feel instant.
- Query keys must be consistent and documented with a comment.

## File Locations

```
frontend/src/hooks/
├── useLikePost.ts
├── useComments.ts
├── useAddComment.ts
└── useSavePost.ts
```

---

## Checklist

### `useLikePost.ts`

- [x] Create `useLikePost(postId: string)` hook:

  ```typescript
  export function useLikePost(postId: string) {
    const queryClient = useQueryClient();

    return useMutation({
      mutationFn: (liked: boolean) =>
        liked ? unlikePost(postId) : likePost(postId),

      // Optimistic update
      onMutate: async (liked: boolean) => {
        await queryClient.cancelQueries({ queryKey: ['post', postId] });
        const previous = queryClient.getQueryData<Post>(['post', postId]);

        queryClient.setQueryData<Post>(['post', postId], (old) =>
          old
            ? {
                ...old,
                likedByCurrentUser: !liked,
                likeCount: liked ? old.likeCount - 1 : old.likeCount + 1,
              }
            : old
        );

        return { previous };
      },

      // Roll back on error
      onError: (_err, _liked, context) => {
        if (context?.previous) {
          queryClient.setQueryData(['post', postId], context.previous);
        }
      },

      onSettled: () => {
        queryClient.invalidateQueries({ queryKey: ['post', postId] });
      },
    });
  }
  ```

- [x] Import `likePost`, `unlikePost` from `../api/likesApi`
- [x] Import `Post` type from `../types/post`

---

### `useComments.ts`

- [x] Create `useComments(postId: string)` hook using `useInfiniteQuery`:

  ```typescript
  export function useComments(postId: string) {
    return useInfiniteQuery({
      queryKey: ['comments', postId],
      queryFn: ({ pageParam = 0 }) => getComments(postId, pageParam, 20),
      getNextPageParam: (lastPage) =>
        lastPage.last ? undefined : lastPage.page + 1,
      initialPageParam: 0,
    });
  }
  ```

- [x] Import `getComments` from `../api/commentsApi`

---

### `useAddComment.ts`

- [x] Create `useAddComment(postId: string)` hook:

  ```typescript
  export function useAddComment(postId: string) {
    const queryClient = useQueryClient();

    return useMutation({
      mutationFn: (payload: AddCommentPayload) => addComment(postId, payload),

      onSuccess: () => {
        // Invalidate to refresh the comment list
        queryClient.invalidateQueries({ queryKey: ['comments', postId] });
        // Also invalidate post to update comment_count
        queryClient.invalidateQueries({ queryKey: ['post', postId] });
      },
    });
  }
  ```

- [x] Import `addComment`, `AddCommentPayload` from `../api/commentsApi`

---

### `useSavePost.ts`

- [x] Create `useSavePost(postId: string)` hook with optimistic update:

  ```typescript
  export function useSavePost(postId: string) {
    const queryClient = useQueryClient();

    return useMutation({
      mutationFn: (saved: boolean) =>
        saved ? unsavePost(postId) : savePost(postId),

      onMutate: async (saved: boolean) => {
        await queryClient.cancelQueries({ queryKey: ['post', postId] });
        const previous = queryClient.getQueryData<Post>(['post', postId]);

        queryClient.setQueryData<Post>(['post', postId], (old) =>
          old ? { ...old, savedByCurrentUser: !saved } : old
        );

        return { previous };
      },

      onError: (_err, _saved, context) => {
        if (context?.previous) {
          queryClient.setQueryData(['post', postId], context.previous);
        }
      },

      onSettled: () => {
        queryClient.invalidateQueries({ queryKey: ['post', postId] });
        // Also invalidate saved posts list
        queryClient.invalidateQueries({ queryKey: ['savedPosts'] });
      },
    });
  }
  ```

- [x] Import `savePost`, `unsavePost` from `../api/savesApi`
