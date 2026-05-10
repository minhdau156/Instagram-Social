import { api } from "./client";
import type { FeedPage } from "../types/post";
import type { TrendingHashtag } from "../types/hashtag";

/**
 * Fetches the authenticated user's home feed.
 * cursor: UUID string of last seen post (omit for first page).
 */
export async function getHomeFeed(cursor?: string): Promise<FeedPage> {
    const params: Record<string, string | number> = { limit: 20 };
    if (cursor) params.cursor = cursor;

    const { data } = await api.get('/api/v1/feed', { params });
    return data.data as FeedPage;
}

/**
 * Fetches the explore feed (posts not from followed users, ranked by engagement).
 * cursor: UUID string of last seen post (omit for first page).
 */
export async function getExploreFeed(cursor?: string): Promise<FeedPage> {
    const params: Record<string, string | number> = { limit: 20 };
    if (cursor) params.cursor = cursor;

    const { data } = await api.get('/api/v1/explore', { params });
    return data.data as FeedPage;
}

/**
 * Fetches the top trending hashtags (max 10 by default).
 */
export async function getTrendingHashtags(limit = 10): Promise<TrendingHashtag[]> {
    const { data } = await api.get('/api/v1/explore/hashtags', {
        params: { limit },
    });
    return data.data as TrendingHashtag[];
}