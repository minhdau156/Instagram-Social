package com.instagram.domain.exception;

import com.instagram.domain.model.RoleName;
import java.util.UUID;

public class RoleNotAssignedException extends RuntimeException {

    public RoleNotAssignedException(UUID userId, RoleName roleName) {
        super("User " + userId + " does not have role " + roleName);
    }
}
