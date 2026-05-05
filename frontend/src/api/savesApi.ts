import type { SavedPostPage } from '../types/save';
import { api } from './client';

const BASE = '/api/v1';

export async function savePost(postId: string): Promise<void> {
    await api.post(`${BASE}/posts/${postId}/save`);
}

export async function unsavePost(postId: string): Promise<void> {
    await api.delete(`${BASE}/posts/${postId}/save`);
}

export async function getSavedPosts(page = 0, size = 20): Promise<SavedPostPage> {
    const { data } = await api.get(`${BASE}/users/me/saved`, {
        params: { page, size },
    });
    return data.data;
}
