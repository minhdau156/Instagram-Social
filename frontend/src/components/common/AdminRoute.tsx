import { Navigate } from "react-router-dom";
import { useAuth } from "../../hooks/useAuth";
import { PageLoader } from "./PageLoader";
import { toast } from "react-toastify";
import { useEffect } from "react";

interface AdminRouteProps {
    children: React.ReactNode;
}

export function AdminRoute({ children }: AdminRouteProps) {
    const { profile, isLoading, isAdmin } = useAuth();

    useEffect(() => {
        if (!isLoading && profile && !isAdmin) {
            toast.error("You don't have permission to access this page.");
        }
    }, [isLoading, profile, isAdmin]);

    if (isLoading) {
        return <PageLoader />;
    }

    if (!profile) {
        return <Navigate to="/login" replace />;
    }

    if (!isAdmin) {
        return <Navigate to="/" replace />;
    }

    return <>{children}</>;
}
