package com.instagram.adapter.out.persistence;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.adapter.out.persistence.entity.PermissionJpaEntity;
import com.instagram.adapter.out.persistence.entity.RoleJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserRoleId;
import com.instagram.adapter.out.persistence.entity.UserRoleJpaEntity;
import com.instagram.adapter.out.persistence.repository.PermissionJpaRepository;
import com.instagram.adapter.out.persistence.repository.RoleJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserRoleJpaRepository;
import com.instagram.domain.model.Permission;
import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.Role;
import com.instagram.domain.model.RoleName;
import com.instagram.domain.port.out.PermissionRepository;
import com.instagram.domain.port.out.RoleRepository;

@Component
public class RbacPersistenceAdapter implements RoleRepository, PermissionRepository {
    private final RoleJpaRepository roleJpaRepository;
    private final PermissionJpaRepository permissionJpaRepository;
    private final UserRoleJpaRepository userRoleJpaRepository;

    public RbacPersistenceAdapter(RoleJpaRepository roleJpaRepository,
            PermissionJpaRepository permissionJpaRepository,
            UserRoleJpaRepository userRoleJpaRepository) {
        this.roleJpaRepository = roleJpaRepository;
        this.permissionJpaRepository = permissionJpaRepository;
        this.userRoleJpaRepository = userRoleJpaRepository;
    }

    @Override
    public Optional<Permission> findByName(PermissionName name) {
        return this.permissionJpaRepository.findByName(name.name()).map(this::toPermissionDomain);
    }

    @Override
    public Set<Permission> findByIds(Collection<UUID> ids) {
        Set<Permission> permissions = this.permissionJpaRepository.findByIdIn(ids).stream()
                .map(this::toPermissionDomain).collect(Collectors.toSet());
        return permissions;
    }

    @Override
    public Optional<Role> findByName(RoleName name) {
        return this.roleJpaRepository.findByName(name.name()).map(this::toRoleDomain);
    }

    @Override
    public Optional<Role> findById(UUID roleId) {
        return this.roleJpaRepository.findById(roleId).map(this::toRoleDomain);
    }

    @Override
    public Set<Role> findRolesByUserId(UUID userId) {
        return this.roleJpaRepository.findByUserId(userId).stream()
                .map(this::toRoleDomain).collect(Collectors.toSet());
    }

    @Override
    public Set<PermissionName> findPermissionNamesByUserId(UUID userId) {
        return userRoleJpaRepository.findPermissionNamesByUserId(userId).stream()
                .map(PermissionName::valueOf)
                .collect(Collectors.toSet());
    }

    @Override
    public void assignRoleToUser(UUID userId, UUID roleId, UUID assignedBy) {
        userRoleJpaRepository.save(toEntity(userId, roleId, assignedBy));
    }

    @Override
    public void revokeRoleFromUser(UUID userId, UUID roleId) {
        userRoleJpaRepository.deleteByUserIdAndRoleId(userId, roleId);
    }

    @Override
    public boolean userHasRole(UUID userId, RoleName name) {
        return roleJpaRepository.findByName(name.name())
                .map(role -> userRoleJpaRepository.existsByIdUserIdAndIdRoleId(userId, role.getId()))
                .orElse(false);
    }

    @Override
    public long countUsersWithRole(RoleName name) {
        return roleJpaRepository.findByName(name.name())
                .map(role -> userRoleJpaRepository.countByIdRoleId(role.getId()))
                .orElse(0L);
    }

    @Override
    @Transactional
    public void replaceRolePermissions(UUID roleId, Set<UUID> permissionIds) {
        RoleJpaEntity role = roleJpaRepository.findById(roleId)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + roleId));
        Set<PermissionJpaEntity> permissions = permissionJpaRepository.findByIdIn(permissionIds).stream()
                .collect(Collectors.toSet());
        role.setPermissions(permissions);
        roleJpaRepository.save(role);
    }

    @Override
    public List<Permission> findAllPermissions() {
        return permissionJpaRepository.findAll().stream().map(this::toPermissionDomain).toList();
    }

    @Override
    public List<Role> findAllRoles() {
        return roleJpaRepository.findAllWithPermissions().stream().map(this::toRoleDomain).toList();
    }

    private Role toRoleDomain(RoleJpaEntity entity) {
        Set<Permission> permissions = entity.getPermissions() == null
                ? Collections.emptySet()
                : entity.getPermissions().stream()
                        .map(this::toPermissionDomain)
                        .collect(Collectors.toSet());
        return Role.builder()
                .id(entity.getId())
                .name(RoleName.valueOf(entity.getName()))
                .description(entity.getDescription())
                .system(entity.isSystem())
                .permissions(permissions)
                .build();
    }

    private Permission toPermissionDomain(PermissionJpaEntity entity) {
        return Permission.builder()
                .id(entity.getId())
                .name(PermissionName.valueOf(entity.getName()))
                .description(entity.getDescription())
                .build();
    }

    private UserRoleJpaEntity toEntity(UUID userId, UUID roleId, UUID assignedBy) {
        return UserRoleJpaEntity.builder()
                .id(new UserRoleId(userId, roleId))
                .assignedBy(assignedBy)
                .build();
    }

}
