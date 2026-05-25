import { useQuery } from "@tanstack/react-query";
import { adminApi } from "../../api/adminApi";
import type { Report, ReportStatus } from "../../types/moderation";

export const useAdminReports = (status?: ReportStatus, page = 0, size = 20) => {
    const { data, isLoading, isError } = useQuery<Report[]>({
        queryKey: ['admin-reports', status, page],
        queryFn: () => adminApi.getReports(status, page, size),
    });
    return { reports: data ?? [], isLoading, isError };
};
