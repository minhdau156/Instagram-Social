import { MyGrants, Permission, PermissionName, Role, RoleName, UserRoles } from "../types/rbac";
import { api } from "./client";

export const rbacApi = {
    listRoles: async (): Promise<Role[]> => {
        const { data } = await api.get("/api/v1/admin/roles");
        return data.data;
    },

    listPermissions: async (): Promise<Permission[]> => {
        const { data } = await api.get("/api/v1/admin/permissions");
        return data.data;
    },

    updateRolePermissions: async (roleName: RoleName, permissions: PermissionName[]): Promise<Role> => {
        const { data } = await api.put(`/api/v1/admin/roles/${roleName}/permissions`, permissions);
        return data.data;
    },

    getUserRoles: async (userId: string): Promise<UserRoles> => {
        const { data } = await api.get(`/api/v1/admin/users/${userId}/roles`);
        return data.data;
    },

    assignRole: async (userId: string, roleName: RoleName): Promise<UserRoles> => {
        const { data } = await api.post(`/api/v1/admin/users/${userId}/roles`, { roleName });
        return data.data;
    },

    revokeRole: async (userId: string, roleName: RoleName): Promise<void> => {
        await api.delete(`/api/v1/admin/users/${userId}/roles/${roleName}`);
    },

    getMyGrants: async (): Promise<MyGrants> => {
        const { data } = await api.get(`/api/v1/users/me/permissions`);
        return data.data;
    },


}