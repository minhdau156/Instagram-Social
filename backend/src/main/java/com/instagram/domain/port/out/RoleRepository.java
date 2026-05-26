package com.instagram.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.Role;
import com.instagram.domain.model.RoleName;

public interface RoleRepository {
    Optional<Role> findByName(RoleName name);

    Optional<Role> findById(UUID roleId);

    List<Role> findAll();

    Set<Role> findRolesByUserId(UUID userId);

    Set<PermissionName> findPermissionNamesByUserId(UUID userId);

    void assignRoleToUser(UUID userId, UUID roleId, UUID assignedBy);

    void revokeRoleFromUser(UUID userId, UUID roleId);

    boolean userHasRole(UUID userId, RoleName name);

    long countUsersWithRole(RoleName name);

    void replaceRolePermissions(UUID roleId, Set<UUID> permissionIds);
}
