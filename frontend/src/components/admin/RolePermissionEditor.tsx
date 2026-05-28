import { useState, useEffect } from "react";
import { Permission, PermissionName, Role } from "../../types/rbac";
import { Box, Button, Checkbox, FormControlLabel, FormGroup, Tooltip, Typography } from "@mui/material";
import theme from "../../theme";

interface RolePermissionEditorProps {
    role: Role;
    allPermissions: Permission[];
    onSave: (permissions: PermissionName[]) => void;
    disabled?: boolean;
}

export const RolePermissionEditor = ({
    role,
    allPermissions,
    onSave,
    disabled
}: RolePermissionEditorProps) => {

    const [selected, setSelected] = useState<PermissionName[]>(role.permissions);
    useEffect(() => {
        setSelected(role.permissions);
    }, [role.id]);

    function arraysEqual(a: PermissionName[], b: PermissionName[]): boolean {
        const copy1 = [...a].sort();
        const copy2 = [...b].sort();
        return copy1.length === copy2.length && copy1.every((v, i) => v === copy2[i]);
    }

    return (
        <Box>
            <Typography variant="subtitle1">{role.name}</Typography>
            <FormGroup>
                {allPermissions.map((permission) => {
                    const isSuperAdminLocked =
                        role.name === 'SUPER_ADMIN' && permission.name === 'ROLE_PERMISSION_MANAGE';
                    return (
                        <Tooltip
                            key={permission.name}
                            title={isSuperAdminLocked
                                ? "This permission cannot be removed from SUPER_ADMIN — the backend enforces it."
                                : ""}
                        >
                            <FormControlLabel
                                control={
                                    <Checkbox
                                        checked={isSuperAdminLocked ? true : selected.includes(permission.name)}
                                        disabled={disabled || isSuperAdminLocked}
                                        onChange={(e) => {
                                            if (e.target.checked) {
                                                setSelected([...selected, permission.name]);
                                            } else {
                                                setSelected(selected.filter((n) => n !== permission.name));
                                            }
                                        }}
                                    />
                                }
                                label={permission.name}
                            />
                        </Tooltip>
                    );
                })}
            </FormGroup>
            <Box sx={{ display: 'flex', justifyContent: 'flex-end', mt: theme.spacing(1) }}>
                <Button
                    variant="contained"
                    onClick={() => onSave(selected)}
                    disabled={disabled || arraysEqual(selected, role.permissions)}
                >
                    Save permissions
                </Button>
            </Box>
        </Box>
    );
};
