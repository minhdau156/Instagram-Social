import { useQuery } from '@tanstack/react-query';
import { rbacApi } from '../api/rbacApi';
import { PermissionName, RoleName } from '../types/rbac';

export function usePermissions() {
    const { data, isLoading } = useQuery({
        queryKey: ['me', 'grants'],
        queryFn: rbacApi.getMyGrants,
        staleTime: 5 * 60 * 1000,
        retry: false,
    });

    const roles = data?.roles ?? [];
    const permissions = data?.permissions ?? [];

    const hasPermission = (p: PermissionName): boolean => {
        if (isLoading) return false;
        return permissions.includes(p);
    };

    const hasRole = (r: RoleName): boolean => {
        if (isLoading) return false;
        return roles.includes(r);
    };

    const hasAnyRole = (rs: RoleName[]): boolean => {
        if (isLoading) return false;
        return rs.some(r => roles.includes(r));
    };

    return { roles, permissions, hasPermission, hasRole, hasAnyRole, isLoading };
}
