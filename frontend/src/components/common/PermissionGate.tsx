import { ReactNode } from 'react';
import { PermissionName, RoleName } from '../../types/rbac';
import { usePermissions } from '../../hooks/usePermissions';

interface PermissionGateProps {
    permission?: PermissionName | PermissionName[];
    role?: RoleName | RoleName[];
    requireAll?: boolean;
    fallback?: ReactNode;
    children: ReactNode;
}

export function PermissionGate({ permission, role, requireAll = false, fallback = null, children }: PermissionGateProps) {
    const { hasPermission, hasRole, isLoading } = usePermissions();

    if (isLoading) return <>{fallback}</>;

    const perms = permission ? (Array.isArray(permission) ? permission : [permission]) : [];
    const roles = role ? (Array.isArray(role) ? role : [role]) : [];

    const checks: boolean[] = [
        ...perms.map(p => hasPermission(p)),
        ...roles.map(r => hasRole(r)),
    ];

    if (checks.length === 0) return <>{children}</>;

    const allowed = requireAll ? checks.every(Boolean) : checks.some(Boolean);

    return <>{allowed ? children : fallback}</>;
}
