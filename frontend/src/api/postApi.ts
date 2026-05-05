import { Page } from '../types/common';
import type { CreatePostPayload, Post, PostPage, UpdatePostPayload } from '../types/post';
import { api } from './client';

const BASE = '/api/v1/posts';

export const postApi = {
  createPost: async (payload: CreatePostPayload): Promise<Post> => {
    const { data } = await api.post(BASE, payload);
    return data.data;
  },

  getPostById: async (id: string): Promise<Post> => {
    const { data } = await api.get(`${BASE}/${id}`);
    return data.data;
  },

  updatePost: async (id: string, payload: UpdatePostPayload): Promise<Post> => {
    const { data } = await api.put(`${BASE}/${id}`, payload);
    return data.data;
  },

  deletePost: async (id: string): Promise<void> => {
    await api.delete(`${BASE}/${id}`);
  },

  getUserPosts: async (userId: string, page: number, size: number = 12): Promise<PostPage> => {
    const { data } = await api.get(`${BASE}/users/${userId}/posts`, {
      params: { page, size }
    });
    return data.data;
  },
};