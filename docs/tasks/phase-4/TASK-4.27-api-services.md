# TASK-4.27 — API Services: Likes, Comments, Saves, Shares

## Overview

Create Axios-based API service modules for all four social interaction feature areas. Each service module is a plain TypeScript file with named async functions — no classes, no singletons beyond the shared `axiosInstance`.

## Requirements

- Lives in `frontend/src/api/`.
- Uses the existing shared `axiosInstance` (already configured with base URL and auth headers).
- Follow the naming and structure of existing services (e.g., `followApi.ts`).
- All functions are `async` and return typed promises.
- No business logic — pure HTTP calls.

## File Locations

```
frontend/src/api/
├── likesApi.ts
├── commentsApi.ts
├── savesApi.ts
└── sharesApi.ts
```

---

## Checklist

### `likesApi.ts`

- [x] Create with the following functions:

  ```typescript
  import axiosInstance from './axiosInstance';
  import type { UserSummary, UserSummaryPage } from '../types/follow';

  /** Like a post. Returns 204 No Content. */
  export async function likePost(postId: string): Promise<void> {
    await axiosInstance.post(`/posts/${postId}/like`);
  }

  /** Unlike a post. Returns 204 No Content. */
  export async function unlikePost(postId: string): Promise<void> {
    await axiosInstance.delete(`/posts/${postId}/like`);
  }

  /** Like a comment. Returns 204 No Content. */
  export async function likeComment(commentId: string): Promise<void> {
    await axiosInstance.post(`/comments/${commentId}/like`);
  }

  /** Unlike a comment. Returns 204 No Content. */
  export async function unlikeComment(commentId: string): Promise<void> {
    await axiosInstance.delete(`/comments/${commentId}/like`);
  }

  /** Get paginated list of users who liked a post. */
  export async function getPostLikers(
    postId: string,
    page = 0,
    size = 20
  ): Promise<UserSummaryPage> {
    const { data } = await axiosInstance.get(`/posts/${postId}/likers`, {
      params: { page, size },
    });
    return data.data;
  }
  ```

---

### `commentsApi.ts`

- [x] Create with the following functions:

  ```typescript
  import axiosInstance from './axiosInstance';
  import type { Comment, CommentPage, AddCommentPayload, EditCommentPayload } from '../types/comment';

  export async function getComments(postId: string, page = 0, size = 20): Promise<CommentPage> {
    const { data } = await axiosInstance.get(`/posts/${postId}/comments`, {
      params: { page, size },
    });
    return data.data;
  }

  export async function addComment(postId: string, payload: AddCommentPayload): Promise<Comment> {
    const { data } = await axiosInstance.post(`/posts/${postId}/comments`, payload);
    return data.data;
  }

  export async function getReplies(commentId: string, page = 0, size = 10): Promise<CommentPage> {
    const { data } = await axiosInstance.get(`/comments/${commentId}/replies`, {
      params: { page, size },
    });
    return data.data;
  }

  export async function editComment(commentId: string, payload: EditCommentPayload): Promise<Comment> {
    const { data } = await axiosInstance.put(`/comments/${commentId}`, payload);
    return data.data;
  }

  export async function deleteComment(commentId: string): Promise<void> {
    await axiosInstance.delete(`/comments/${commentId}`);
  }
  ```

---

### `savesApi.ts`

- [x] Create with the following functions:

  ```typescript
  import axiosInstance from './axiosInstance';
  import type { SavedPost, SavedPostPage } from '../types/save';

  export async function savePost(postId: string): Promise<void> {
    await axiosInstance.post(`/posts/${postId}/save`);
  }

  export async function unsavePost(postId: string): Promise<void> {
    await axiosInstance.delete(`/posts/${postId}/save`);
  }

  export async function getSavedPosts(page = 0, size = 20): Promise<SavedPostPage> {
    const { data } = await axiosInstance.get('/users/me/saved', {
      params: { page, size },
    });
    return data.data;
  }
  ```

- [x] Create minimal `frontend/src/types/save.ts`:
  ```typescript
  export interface SavedPost {
    id: string;
    postId: string;
    userId: string;
    savedAt: string;
  }

  export interface SavedPostPage {
    content: SavedPost[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    last: boolean;
  }
  ```

---

### `sharesApi.ts`

- [x] Create with the following functions:

  ```typescript
  import axiosInstance from './axiosInstance';

  export type ShareType = 'LINK' | 'DM';

  export interface ShareRequest {
    shareType: ShareType;
    recipientId?: string;
  }

  export interface ShareResponse {
    id: string;
    postId: string;
    sharerId: string;
    shareType: ShareType;
    createdAt: string;
  }

  export async function sharePost(postId: string, payload: ShareRequest): Promise<ShareResponse> {
    const { data } = await axiosInstance.post(`/posts/${postId}/share`, payload);
    return data.data;
  }
  ```
