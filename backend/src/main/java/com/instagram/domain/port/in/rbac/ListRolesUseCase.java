package com.instagram.domain.port.in.rbac;

import java.util.List;

import com.instagram.domain.model.Role;

public interface ListRolesUseCase {
    List<Role> listRoles(Query query);

    record Query() {
    }
}
