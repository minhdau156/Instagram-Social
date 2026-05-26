package com.instagram.domain.port.in.rbac;

import java.util.Set;
import java.util.UUID;

import com.instagram.domain.model.Role;

public interface GetUserRolesUseCase {
    Set<Role> getUserRoles(Query query);

    record Query(UUID targetUserId) {
    }
}
