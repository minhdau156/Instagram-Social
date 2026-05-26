package com.instagram.domain.port.in.rbac;

import java.util.Set;
import java.util.UUID;

import com.instagram.domain.model.Role;
import com.instagram.domain.model.RoleName;

public interface AssignRoleToUserUseCase {
    Set<Role> assignRoleToUser(Command command);

    record Command(UUID actorId, UUID targetUserId, RoleName roleName) {

    }
}
