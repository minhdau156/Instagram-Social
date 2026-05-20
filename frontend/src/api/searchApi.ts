import { api } from "./client";
import type { HashtagSearchResult, PostSearchResult, SearchHistoryItem, UserSearchResult } from "../types/search";

export const searchApi = {
    searchUsers: async (q: string, page = 0, size = 20): Promise<UserSearchResult[]> => {
        const { data } = await api.get('/api/v1/search', { params: { q, type: 'users', page, size } });
        return data.data;
    },

    searchHashtags: async (q: string, page = 0, size = 20): Promise<HashtagSearchResult[]> => {
        const { data } = await api.get('/api/v1/search', { params: { q, type: 'hashtags', page, size } });
        return data.data;
    },

    searchPosts: async (q: string, page = 0, size = 20): Promise<PostSearchResult[]> => {
        const { data } = await api.get('/api/v1/search', { params: { q, type: 'posts', page, size } });
        return data.data;
    },

    getSearchHistory: async (): Promise<SearchHistoryItem[]> => {
        const { data } = await api.get('/api/v1/search/history');
        return data.data;
    },

    clearSearchHistory: async (): Promise<void> => {
        await api.delete('/api/v1/search/history');
    },

    getPostsByHashtag: async (hashtagName: string, page = 0, size = 20): Promise<PostSearchResult[]> => {
        const { data } = await api.get(`/api/v1/hashtags/${hashtagName}/posts?page=${page}&size=${size}`);
        return data.data;
    }
}