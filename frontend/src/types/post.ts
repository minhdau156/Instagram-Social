export type MediaType = 'IMAGE' | 'VIDEO';
/** Response shape returned by GET /api/v1/posts and GET /api/v1/posts/:id */
export interface Post {
  id: string;
  userId: string;
  caption?: string;
  location?: string;
  likeCount: number;
  commentCount: number;
  likedByCurrentUser?: boolean;
  savedByCurrentUser?: boolean;
  mediaItems: PostMedia[];
  createdAt: string;
}

export interface PostMedia {
  id: string;
  mediaType: MediaType;
  mediaUrl: string;
  thumbnailUrl?: string;
  width?: number;
  height?: number;
  duration?: number;    // seconds, video only
  fileSizeBytes?: number;
  sortOrder: string;
}

export interface CreatePostPayload {
  caption?: string;
  location?: string;
  mediaItems: MediaItem[];
}

export interface UpdatePostPayload {
  caption?: string;
  location?: string;
}

export interface MediaItem {
  mediaKey: string;
  mediaType: MediaType;
  width?: number;
  height?: number;
  duration?: number;
  fileSizeBytes?: number;
  sortOrder: string;
}

export interface UploadUrlResponse {
  presignedUrl: string;
  mediaKey: string;
}

export interface PostPage {
  content: Post[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

/**
 * Keyset-paginated feed response.
 * nextCursor is the UUID of the last post in this page,
 * or null when there are no more pages.
 */
export interface FeedPage {
  posts: Post[];
  nextCursor: string | null;
}