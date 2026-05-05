import { api } from './client';

const BASE = '/api/v1';

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
    const { data } = await api.post(`${BASE}/posts/${postId}/share`, payload);
    return data.data;
}
