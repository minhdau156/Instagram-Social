import { useMutation, useQuery } from "@tanstack/react-query";
import { rbacApi } from "../../api/rbacApi";
import { AxiosError } from "axios";
import { toast } from "react-toastify";
import { queryClient } from "../../lib/queryClient";
import { RoleName } from "../../types/rbac";
import { Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, MenuItem, Select } from "@mui/material";
import { useState } from "react";
import { PermissionGate } from "../common/PermissionGate";

interface UserRolesPanelProps {
    userId: string;
    onClose?: () => void;
}

export const UserRolesPanel = ({
    userId,
}: UserRolesPanelProps) => {

    const [pendingRevoke, setPendingRevoke] = useState<RoleName | null>(null);
    const [selectedRole, setSelectedRole] = useState<RoleName | null>(null);

    const { data: userRoles, isLoading, isError } = useQuery({
        queryKey: ['admin', 'users', userId, 'roles'],
        queryFn: () => rbacApi.getUserRoles(userId),
        enabled: !!userId,
    });

    const { data: roles } = useQuery({
        queryKey: ['admin', 'roles'],
        queryFn: rbacApi.listRoles,
    });

    const { mutate: assign, isPending: assigning } = useMutation({
        mutationFn: (roleName: RoleName) => rbacApi.assignRole(userId, roleName),
        onSuccess: () => {
            queryClient.invalidateQueries({ queryKey: ['admin', 'users', userId, 'roles'] });
            setSelectedRole(null);
        },
        onError: (error) => {
            const message = error instanceof AxiosError && error.response?.data?.message
                ? error.response.data.message
                : 'Failed to assign role';
            toast.error(message);
        },
    });

    const { mutate: revoke, isPending: revoking } = useMutation({
        mutationFn: (roleName: RoleName) => rbacApi.revokeRole(userId, roleName),
        onSuccess: () => queryClient.invalidateQueries({ queryKey: ['admin', 'users', userId, 'roles'] }),
        onError: (error) => {
            const message = error instanceof AxiosError && error.response?.data?.message
                ? error.response.data.message
                : 'Failed to revoke role';
            toast.error(message);
        },
    });

    if (isLoading) return <CircularProgress />;
    if (isError) return <Alert severity="error">Failed to load roles for this user.</Alert>;

    const onDelete = (roleName: RoleName) => {
        if (roleName === 'ADMIN' || roleName === 'SUPER_ADMIN') {
            setPendingRevoke(roleName);
        } else {
            revoke(roleName);
        }
    };

    const unassignedRoles = roles?.filter(
        (role) => !userRoles?.roles?.some((r) => r.name === role.name)
    );

    return (
        <>
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1, mb: 2 }}>
                {userRoles?.roles?.map(role => (
                    <Chip
                        key={role.name}
                        label={role.name}
                        onDelete={() => onDelete(role.name)}
                    />
                ))}
            </Box>

            <PermissionGate permission="ROLE_ASSIGN">
                <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
                    <Select
                        size="small"
                        value={selectedRole ?? ''}
                        onChange={(e) => setSelectedRole(e.target.value as RoleName)}
                        displayEmpty
                        sx={{ minWidth: 160 }}
                    >
                        <MenuItem value="" disabled>Select role</MenuItem>
                        {unassignedRoles?.map(role => (
                            <MenuItem key={role.name} value={role.name}>{role.name}</MenuItem>
                        ))}
                    </Select>
                    <Button
                        variant="contained"
                        onClick={() => selectedRole && assign(selectedRole)}
                        disabled={assigning || !selectedRole}
                    >
                        Assign
                    </Button>
                </Box>
            </PermissionGate>

            <Dialog open={!!pendingRevoke} onClose={() => setPendingRevoke(null)}>
                <DialogContent>
                    <DialogTitle>Confirm revoke</DialogTitle>
                    <DialogContentText>
                        Are you sure you want to remove {pendingRevoke} from this user?
                    </DialogContentText>
                    <DialogActions>
                        <Button onClick={() => setPendingRevoke(null)} disabled={revoking}>
                            Cancel
                        </Button>
                        <Button
                            onClick={() => pendingRevoke && revoke(pendingRevoke)}
                            disabled={revoking}
                            color="error"
                            variant="contained"
                        >
                            Remove
                        </Button>
                    </DialogActions>
                </DialogContent>
            </Dialog>
        </>
    );
};
