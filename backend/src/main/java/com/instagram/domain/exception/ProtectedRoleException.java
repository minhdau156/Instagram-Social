package com.instagram.domain.exception;

import com.instagram.domain.model.RoleName;

public class ProtectedRoleException extends RuntimeException {

    public ProtectedRoleException(RoleName roleName) {
        super("Role " + roleName + " is protected and cannot be modified");
    }
}
