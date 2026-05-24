import { api } from "./client";
import type { AdminUser, Report, ReportStatus, ReviewReportPayload, SuspendUserPayload } from "../types/moderation";

export const adminApi = {
    getReports: async (status?: ReportStatus, page?: number, size?: number): Promise<Report[]> => {
        return api.get('/api/v1/admin/reports', {
            params: {
                status: status,
                page: page ?? 0,
                size: size ?? 20
            }
        }).then(r => r.data.data);
    },
    reviewReport: async (id: string, payload: ReviewReportPayload): Promise<Report> => {
        return api.put(`/api/v1/admin/reports/${id}`, payload).then(r => r.data.data);
    },
    suspendUser: async (id: string, payload: SuspendUserPayload): Promise<AdminUser> => {
        return api.put(`/api/v1/admin/users/${id}/suspend`, payload).then(r => r.data.data);
    },
    unsuspendUser: async (id: string): Promise<AdminUser> => {
        return api.put(`/api/v1/admin/users/${id}/unsuspend`).then(r => r.data.data);
    },
    getAdminUsers: async (filters?: { username?: string; status?: string }, page?: number, size?: number): Promise<AdminUser[]> => {
        return api.get('/api/v1/admin/users', {
            params: {
                ...filters,
                page: page ?? 0,
                size: size ?? 20
            }
        }).then(r => r.data.data);
    }
}