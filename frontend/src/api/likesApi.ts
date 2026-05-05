import type { UserSummaryPage } from "../types/follow";
import { api } from "./client";

const BASE = "/api/v1";

export async function likePost(postId: string): Promise<void> {
    await api.post(`${BASE}/posts/${postId}/like`);
}

export async function unlikePost(postId: string): Promise<void> {
    await api.delete(`${BASE}/posts/${postId}/like`);
}

export async function likeComment(commentId: string): Promise<void> {
    await api.post(`${BASE}/comments/${commentId}/like`);
}

export async function unlikeComment(commentId: string): Promise<void> {
    await api.delete(`${BASE}/comments/${commentId}/like`);
}

export async function getPostLikers(
    postId: string,
    page: number = 0,
    size: number = 20
): Promise<UserSummaryPage> {
    const { data } = await api.get(`${BASE}/posts/${postId}/likers`, {
        params: { page, size },
    });
    return data.data;
}