# TASK-4.26 — TypeScript Types: Post & Comment

## Overview

Update and create TypeScript interfaces, enums, and type aliases required by the Like, Comment, Save, and Share features. These types are the single source of truth for social interaction data shapes across the frontend.

## Requirements

- Lives in `frontend/src/types/`.
- No runtime logic — only `interface`, `type`, `enum` declarations.
- All types must match the backend DTOs exactly.
- Export everything from the file.

## File Locations

```
frontend/src/types/post.ts         ← UPDATE existing file
frontend/src/types/comment.ts      ← CREATE new file
```

---

## Checklist

### Update `post.ts`

- [x] Add the following fields to the existing `Post` interface:
  ```typescript
  /** Whether the currently authenticated user has liked this post */
  likedByCurrentUser: boolean;

  /** Whether the currently authenticated user has saved this post */
  savedByCurrentUser: boolean;

  /** Total number of times this post has been shared */
  shareCount: number;
  ```

- [x] Verify backward compatibility — these new fields should be optional (`?`) if the backend might not always return them for unauthenticated users:
  ```typescript
  likedByCurrentUser?: boolean;
  savedByCurrentUser?: boolean;
  shareCount?: number;
  ```

---

### Create `comment.ts`

- [x] Create `comment.ts` with the following types:

  ```typescript
  import type { CommentStatus } from './comment';

  // ─── Enums ────────────────────────────────────────────────────────────────

  export enum CommentStatus {
    ACTIVE = 'ACTIVE',
    DELETED = 'DELETED',
  }

  // ─── Core domain types ────────────────────────────────────────────────────

  /** Matches backend CommentResponse DTO */
  export interface Comment {
    id: string;
    postId: string;
    userId: string;
    username: string;
    avatarUrl: string | null;
    parentId: string | null;    // null = top-level comment
    content: string;
    likeCount: number;
    replyCount: number;
    status: CommentStatus;
    createdAt: string;          // ISO-8601
    updatedAt: string;          // ISO-8601
    isLikedByCurrentUser: boolean;
  }

  // ─── API payload types ────────────────────────────────────────────────────

  export interface AddCommentPayload {
    content: string;
    parentId?: string | null;   // omit or null for top-level
  }

  export interface EditCommentPayload {
    content: string;
  }

  /** Paginated response wrapper for comment lists */
  export interface CommentPage {
    content: Comment[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    last: boolean;
  }
  ```

- [x] Verify field names match the backend `CommentResponse` DTO (camelCase matches Spring's default serialization)
- [x] Ensure `CommentStatus` enum values exactly match the Java enum string representation
