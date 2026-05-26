package com.instagram.domain.exception;

import com.instagram.domain.model.RoleName;

public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(RoleName roleName) {
        super("Role not found: " + roleName);
    }
}
