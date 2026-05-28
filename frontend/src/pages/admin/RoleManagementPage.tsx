import React from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { rbacApi } from "../../api/rbacApi";
import { SuperAdminRoute } from "../../components/common/SuperAdminRoute";
import { Alert, Box, CircularProgress, Divider, Typography } from "@mui/material";
import { queryClient } from "../../lib/queryClient";
import { PermissionName, RoleName } from "../../types/rbac";
import { AxiosError } from "axios";
import { toast } from "react-toastify";
import { usePermissions } from "../../hooks/usePermissions";
import { RolePermissionEditor } from "../../components/admin/RolePermissionEditor";
import theme from "../../theme";

export default function RoleManagementPage() {

    const { data: roles, isLoading: rolesLoading, isError: rolesError } = useQuery({
        queryKey: ['admin', 'roles'],
        queryFn: rbacApi.listRoles,
    });

    const { data: allPermissions, isLoading: permsLoading } = useQuery({
        queryKey: ['admin', 'permissions'],
        queryFn: rbacApi.listPermissions,
    });

    const { mutate: savePermissions, isPending } = useMutation({
        mutationFn: ({ roleName, permissions }: { roleName: RoleName; permissions: PermissionName[] }) =>
            rbacApi.updateRolePermissions(roleName, permissions),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['admin', 'roles'] });
            toast.success("Permissions updated successfully!");
        },
        onError: (error) => {
            const message = error instanceof AxiosError && error.response?.data?.message
                ? error.response.data.message
                : "Failed to update permissions.";
            toast.error(message);
        },

    });

    const { hasPermission } = usePermissions();

    const editorDisabled = !hasPermission('ROLE_PERMISSION_MANAGE') || isPending;

    if (rolesLoading || permsLoading) {
        return (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
                <CircularProgress />
            </Box>
        )
    }

    if (rolesError) {
        return (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh' }}>
                <Alert severity="error">Failed to load roles. Please refresh.</Alert>
            </Box>
        )
    }



    return (
        <SuperAdminRoute>
            <Box sx={{ p: 3 }}>
                <Typography variant="h5">Roles & Permissions</Typography>
                {roles?.map(role => (
                    <React.Fragment key={role.name}>
                        <RolePermissionEditor
                            role={role}
                            allPermissions={allPermissions || []}
                            onSave={(permissions) => savePermissions({ roleName: role.name, permissions })}
                            disabled={editorDisabled}
                        />
                        <Divider sx={{ my: theme.spacing(3) }} />
                    </React.Fragment>
                ))}
            </Box>
        </SuperAdminRoute>
    )
}