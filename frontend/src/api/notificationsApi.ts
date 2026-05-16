import { api } from "./client";
import type { Notification, NotificationSettings, RegisterDeviceTokenPayload } from "../types/notification";

const BASE_URL = '/api/v1/notifications';

export const notificationsApi = {
    getNotifications: async (page = 0, size = 20): Promise<Notification[]> => {
        const { data } = await api.get(BASE_URL, { params: { page, size } });
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