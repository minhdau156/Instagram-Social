import { useEffect } from 'react';
import { Navigate } from 'react-router-dom';
import { toast } from 'react-toastify';
import { useAuth } from '../../hooks/useAuth';
import { usePermissions } from '../../hooks/usePermissions';
import { PageLoader } from './PageLoader';

interface SuperAdminRouteProps {
    children: React.ReactNode;
}

export function SuperAdminRoute({ children }: SuperAdminRouteProps) {
    const { profile, isLoading: authLoading } = useAuth();
    const { hasRole, isLoading: grantsLoading } = usePermissions();

    const isSuperAdmin = hasRole('SUPER_ADMIN');

    useEffect(() => {
        if (!authLoading && !grantsLoading && profile && !isSuperAdmin) {
            toast.error("You don't have permission to access this page.");
        }
    }, [authLoading, grantsLoading, profile, isSuperAdmin]);

    if (authLoading) return <PageLoader />;
    if (!profile) return <Navigate to="/login" replace />;
    if (grantsLoading) return <PageLoader />;
    if (!isSuperAdmin) return <Navigate to="/" replace />;

    return <>{children}</>;
}
