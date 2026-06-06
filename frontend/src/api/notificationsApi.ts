import { api } from "./client";
import type { Notification, NotificationSettings, RegisterDeviceTokenPayload } from "../types/notification";

const BASE_URL = '/api/v1/notifications';

export const notificationsApi = {
    getNotifications: async (cursor: string | null = null, size = 20): Promise<{ items: Notification[]; nextCursor: string | null; hasMore: boolean }> => {
        const params: Record<string, unknown> = { size };
        if (cursor) params.cursor = cursor;
        const { data } = await api.get(BASE_URL, { params });
        return data.data;
    },

    markRead: async (id: string): Promise<void> => {
        await api.put(`${BASE_URL}/${id}/read`);
    },

    markAllRead: async (): Promise<void> => {
        await api.put(`${BASE_URL}/read-all`);
    },

    getSettings: async (): Promise<NotificationSettings> => {
        const { data } = await api.get(`${BASE_URL}/settings`);
        return data.data;
    },

    updateSettings: async (settings: NotificationSettings): Promise<NotificationSettings> => {
        const { data } = await api.put(`${BASE_URL}/settings`, settings);
        return data.data;
    },

    registerDeviceToken: async (payload: RegisterDeviceTokenPayload): Promise<void> => {
        await api.post('/api/v1/device-tokens', payload);
    }
}