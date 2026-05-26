package com.instagram.domain.port.in.rbac;

import java.util.UUID;

public interface AssignDefaultRoleUseCase {
    void assignDefaultRole(Command command);

    record Command(UUID userId) {
    }
}
