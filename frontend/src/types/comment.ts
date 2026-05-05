export enum CommentStatus {
    ACTIVE = 'ACTIVE',
    DELETED = 'DELETED'
}

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


export interface AddCommentPayload {
    content: string;
    parentId?: string | null;   // omit or null for top-level
}

export interface EditCommentPayload {
    content: string;
}

export interface CommentPage {
    content: Comment[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
    last: boolean;
}