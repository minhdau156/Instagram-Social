import { useQuery } from "@tanstack/react-query";
import { adminApi } from "../../api/adminApi";
import type { AdminUser } from "../../types/moderation";

interface AdminUsersFilters {
    username?: string;
    status?: string;
}

export const useAdminUsers = (filters?: AdminUsersFilters, page = 0, size = 20) => {
    const { data, isLoading, isError } = useQuery<AdminUser[]>({
        queryKey: ['admin-users', filters, page],
        queryFn: () => adminApi.getAdminUsers(filters, page, size),
    });
    return { users: data ?? [], isLoading, isError };
};
