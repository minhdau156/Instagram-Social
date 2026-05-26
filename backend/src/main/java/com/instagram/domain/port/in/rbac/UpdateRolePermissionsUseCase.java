package com.instagram.domain.port.in.rbac;

import java.util.Set;
import java.util.UUID;

import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.RoleName;

public interface UpdateRolePermissionsUseCase {
    void updateRolePermissions(Command command);

    record Command(UUID actorId, RoleName roleName, Set<PermissionName> permissions) {
    }
}
