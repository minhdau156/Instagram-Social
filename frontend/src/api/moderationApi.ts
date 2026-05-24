import { api } from "./client";
import type { Report, SubmitReportPayload, UserBlock } from "../types/moderation";

export const moderationApi = {
    submitReport: async (payload: SubmitReportPayload): Promise<Report> => {
        return api.post('/api/v1/reports', payload).then(r => r.data.data);
    },
    blockUser: async (username: string): Promise<void> => {
        return api.post(`/api/v1/users/${username}/block`).then(() => undefined);
    },
    unblockUser: async (username: string): Promise<void> => {
        return api.delete(`/api/v1/users/${username}/block`).then(() => undefined);
    },
    getBlockedUsers: async (page?: number, size?: number): Promise<UserBlock[]> => {
        return api.get('/api/v1/users/me/blocked', {
            params: {
                page: page ?? 0,
                size: size ?? 20
            }
        }).then(r => r.data.data);
    }
}