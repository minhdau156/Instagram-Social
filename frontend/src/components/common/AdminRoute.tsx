import { useEffect } from "react";
import { Navigate } from "react-router-dom";
import { toast } from "react-toastify";
import { useAuth } from "../../hooks/useAuth";
import { usePermissions } from "../../hooks/usePermissions";
import { PageLoader } from "./PageLoader";

interface AdminRouteProps {
    children: React.ReactNode;
}

export function AdminRoute({ children }: AdminRouteProps) {
    const { profile, isLoading: authLoading } = useAuth();
    const { hasAnyRole, isLoading: grantsLoading } = usePermissions();

    const hasAdminAccess = hasAnyRole(['MODERATOR', 'ADMIN', 'SUPER_ADMIN']);

    useEffect(() => {
        if (!authLoading && !grantsLoading && profile && !hasAdminAccess) {
            toast.error("You don't have permission to access this page.");
        }
    }, [authLoading, grantsLoading, profile, hasAdminAccess]);

    if (authLoading) return <PageLoader />;
    if (!profile) return <Navigate to="/login" replace />;
    if (grantsLoading) return <PageLoader />;
    if (!hasAdminAccess) return <Navigate to="/" replace />;

    return <>{children}</>;
}
