import { api } from "./client";
import type { User, UserProfile, UpdateProfilePayload } from "../types/user";

export const usersApi = {
    getMe: () =>
        api.get<{ data: UserProfile }>('/api/v1/users/profile/get').then(r => r.data.data),

    updateMe: (payload: UpdateProfilePayload) =>
        api.put<{ data: User }>('/api/v1/users/profile/update', payload).then(r => r.data.data),

    getUserByUsername: (username: string) =>
        api.get<{ data: UserProfile }>(`/api/v1/users/${username}/bio`).then(r => r.data.data),

    uploadAvatar: (file: File) => {
        const form = new FormData();
        form.append('file', file);
        return api.put<{ data: User }>('/api/v1/users/profile/avatar', form, {
            headers: { 'Content-Type': 'multipart/form-data' },
        }).then(r => r.data.data);
    },
    getUserById: (id: string) =>
        api.get<{ data: User }>(`/api/v1/users/get/${id}`).then(r => r.data.data),

    search: (q: string, limit = 10) =>
        api.get<{ data: User[] }>('/api/v1/users/search', { params: { q, limit } }).then(r => r.data.data),
}