package com.instagram.application.service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.domain.exception.InsufficientPrivilegeException;
import com.instagram.domain.exception.ProtectedRoleException;
import com.instagram.domain.exception.RoleAlreadyAssignedException;
import com.instagram.domain.exception.RoleNotAssignedException;
import com.instagram.domain.exception.RoleNotFoundException;
import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.Role;
import com.instagram.domain.model.RoleName;
import com.instagram.domain.port.in.rbac.AssignDefaultRoleUseCase;
import com.instagram.domain.port.in.rbac.AssignRoleToUserUseCase;
import com.instagram.domain.port.in.rbac.GetUserPermissionsUseCase;
import com.instagram.domain.port.in.rbac.GetUserRolesUseCase;
import com.instagram.domain.port.in.rbac.ListRolesUseCase;
import com.instagram.domain.port.in.rbac.RevokeRoleFromUserUseCase;
import com.instagram.domain.port.in.rbac.UpdateRolePermissionsUseCase;
import com.instagram.domain.port.out.AuditLogRepository;
import com.instagram.domain.port.out.PermissionRepository;
import com.instagram.domain.port.out.RoleRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class RbacService implements AssignDefaultRoleUseCase, AssignRoleToUserUseCase,
        GetUserPermissionsUseCase, GetUserRolesUseCase,
        ListRolesUseCase, RevokeRoleFromUserUseCase,
        UpdateRolePermissionsUseCase {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final AuditLogRepository auditLogRepository;

    @Override
    @Transactional
    public Set<Role> assignRoleToUser(AssignRoleToUserUseCase.Command command) {
        Role role = roleRepository.findByName(command.roleName())
                .orElseThrow(() -> new RoleNotFoundException(command.roleName()));

        Set<Role> actorRoles = roleRepository.findRolesByUserId(command.actorId());
        boolean actorIsSuperAdmin = actorRoles.stream().anyMatch(r -> r.getName() == RoleName.SUPER_ADMIN);
        boolean actorIsAdmin = actorRoles.stream().anyMatch(r -> r.getName() == RoleName.ADMIN);

        if (!actorIsSuperAdmin) {
            if (!actorIsAdmin || command.roleName() == RoleName.ADMIN || command.roleName() == RoleName.SUPER_ADMIN) {
                throw new InsufficientPrivilegeException(
                        "Insufficient privileges to assign role " + command.roleName());
            }
        }

        if (roleRepository.userHasRole(command.targetUserId(), command.roleName())) {
            throw new RoleAlreadyAssignedException(command.targetUserId(), command.roleName());
        }

        roleRepository.assignRoleToUser(command.targetUserId(), role.getId(), command.actorId());

        auditLogRepository.log(command.actorId(), AuditLogRepository.ROLE_ASSIGNED,
                "USER", command.targetUserId(), "role=" + command.roleName(), null);

        return roleRepository.findRolesByUserId(command.targetUserId());
    }

    @Override
    @Transactional
    public void revokeRoleFromUser(RevokeRoleFromUserUseCase.Command command) {
        Role role = roleRepository.findByName(command.roleName())
                .orElseThrow(() -> new RoleNotFoundException(command.roleName()));

        Set<Role> actorRoles = roleRepository.findRolesByUserId(command.actorId());
        boolean actorIsSuperAdmin = actorRoles.stream().anyMatch(r -> r.getName() == RoleName.SUPER_ADMIN);
        boolean actorIsAdmin = actorRoles.stream().anyMatch(r -> r.getName() == RoleName.ADMIN);

        if (!actorIsSuperAdmin) {
            if (!actorIsAdmin || command.roleName() == RoleName.ADMIN || command.roleName() == RoleName.SUPER_ADMIN) {
                throw new InsufficientPrivilegeException(
                        "Insufficient privileges to assign role " + command.roleName());
            }
        }

        if (!roleRepository.userHasRole(command.targetUserId(), command.roleName())) {
            throw new RoleNotAssignedException(command.targetUserId(), command.roleName());
        }

        if (command.roleName() == RoleName.SUPER_ADMIN
                && roleRepository.countUsersWithRole(RoleName.SUPER_ADMIN) <= 1) {
            throw new InsufficientPrivilegeException("cannot remove the last super-admin");
        }

        roleRepository.revokeRoleFromUser(command.targetUserId(), role.getId());

        auditLogRepository.log(command.actorId(), AuditLogRepository.ROLE_REVOKED, "USER", command.targetUserId(), null,
                null);

    }

    @Override
    public List<Role> listRoles(ListRolesUseCase.Query query) {
        return roleRepository.findAllRoles();
    }

    @Override
    public Set<Role> getUserRoles(GetUserRolesUseCase.Query query) {
        return roleRepository.findRolesByUserId(query.targetUserId());
    }

    @Override
    public Set<PermissionName> getUserPermissions(GetUserPermissionsUseCase.Query query) {
        Set<PermissionName> permissions = roleRepository.findPermissionNamesByUserId(query.userId());
        if (permissions == null) {
            return Collections.emptySet();
        }
        return permissions;
    }

    @Override
    @Transactional
    public void updateRolePermissions(UpdateRolePermissionsUseCase.Command command) {
        Set<Role> actorRoles = roleRepository.findRolesByUserId(command.actorId());
        boolean actorIsSuperAdmin = actorRoles.stream().anyMatch(r -> r.getName() == RoleName.SUPER_ADMIN);
        if (!actorIsSuperAdmin) {
            throw new InsufficientPrivilegeException("Only SUPER_ADMIN can update role permissions");
        }

        if (command.roleName() == RoleName.SUPER_ADMIN
                && !command.permissions().contains(PermissionName.ROLE_PERMISSION_MANAGE)) {
            throw new ProtectedRoleException(RoleName.SUPER_ADMIN);
        }

        Set<UUID> permissionIds = command.permissions().stream()
                .map(p -> permissionRepository.findByName(p)
                        .orElseThrow(() -> new IllegalArgumentException("Permission not found: " + p)).getId())
                .collect(Collectors.toSet());

        Role role = roleRepository.findByName(command.roleName())
                .orElseThrow(() -> new RoleNotFoundException(command.roleName()));

        roleRepository.replaceRolePermissions(role.getId(), permissionIds);
        auditLogRepository.log(command.actorId(), AuditLogRepository.ROLE_PERMISSIONS_UPDATED,
                "ROLE", role.getId(), "permissions=" + command.permissions(), null);

    }

    @Override
    @Transactional
    public void assignDefaultRole(AssignDefaultRoleUseCase.Command command) {
        Role role = roleRepository.findByName(RoleName.USER)
                .orElseThrow(() -> new RoleNotFoundException(RoleName.USER));
        if (!roleRepository.userHasRole(command.userId(), RoleName.USER)) {
            roleRepository.assignRoleToUser(command.userId(), role.getId(), null);
        }
    }
}
