import type { Comment, CommentPage, AddCommentPayload, EditCommentPayload } from '../types/comment';
import { api } from './client';

const BASE = '/api/v1';

type CursorPage<T> = { items: T[]; nextCursor: string | null; hasMore: boolean };

export async function getComments(postId: string, cursor: string | null = null, size = 20): Promise<CursorPage<Comment>> {
    const params: Record<string, unknown> = { size };
    if (cursor) params.cursor = cursor;
    const { data } = await api.get(`${BASE}/posts/${postId}/comments`, { params });
    return data.data;
}

export async function addComment(postId: string, payload: AddCommentPayload): Promise<Comment> {
    const { data } = await api.post(`${BASE}/posts/${postId}/comments`, payload);
    return data.data;
}

export async function getReplies(commentId: string, page = 0, size = 10): Promise<CommentPage> {
    const { data } = await api.get(`${BASE}/comments/${commentId}/replies`, {
        params: { page, size },
    });
    return data.data;
}

export async function editComment(commentId: string, payload: EditCommentPayload): Promise<Comment> {
    const { data } = await api.put(`${BASE}/comments/${commentId}`, payload);
    return data.data;
}

export async function deleteComment(commentId: string): Promise<void> {
    await api.delete(`${BASE}/comments/${commentId}`);
}
