package com.instagram.domain.exception;

import com.instagram.domain.model.RoleName;
import java.util.UUID;

public class RoleAlreadyAssignedException extends RuntimeException {

    public RoleAlreadyAssignedException(UUID userId, RoleName roleName) {
        super("User " + userId + " already has role " + roleName);
    }
}
