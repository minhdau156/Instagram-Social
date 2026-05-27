export type RoleName = 'USER' | 'MODERATOR' | 'ADMIN' | 'SUPER_ADMIN';

export type PermissionName = 'REPORT_VIEW' | 'REPORT_REVIEW' | 'CONTENT_MODERATE' | 'USER_VIEW' | 'USER_SUSPEND' | 'USER_UNSUSPEND' | 'AUDIT_LOG_VIEW' | 'ROLE_VIEW' | 'ROLE_ASSIGN' | 'ROLE_PERMISSION_MANAGE';

export interface Permission {
    id: string;
    name: PermissionName;
    description: string;
}

export interface Role {
    id: string;
    name: RoleName;
    description: string;
    system: boolean;
    permissions: PermissionName[];
}

export interface UserRoles {
    userId: string;
    roles: Role[];
}


export interface MyGrants {
    roles: RoleName[];
    permissions: PermissionName[];
}