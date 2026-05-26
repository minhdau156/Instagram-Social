package com.instagram.domain.port.in.rbac;

import java.util.UUID;

import com.instagram.domain.model.RoleName;

public interface RevokeRoleFromUserUseCase {
    void revokeRoleFromUser(Command command);

    record Command(UUID actorId, UUID targetUserId, RoleName roleName) {
    }
}
