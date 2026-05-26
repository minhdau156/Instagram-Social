package com.instagram.domain.port.in.rbac;

import java.util.Set;
import java.util.UUID;

import com.instagram.domain.model.PermissionName;

public interface GetUserPermissionsUseCase {
    Set<PermissionName> getUserPermissions(Query query);

    record Query(UUID userId) {
    }
}
