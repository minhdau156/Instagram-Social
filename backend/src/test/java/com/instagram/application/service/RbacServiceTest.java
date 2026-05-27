package com.instagram.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.instagram.domain.exception.InsufficientPrivilegeException;
import com.instagram.domain.exception.ProtectedRoleException;
import com.instagram.domain.exception.RoleAlreadyAssignedException;
import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.Role;
import com.instagram.domain.model.RoleName;
import com.instagram.domain.port.in.rbac.AssignDefaultRoleUseCase;
import com.instagram.domain.port.in.rbac.AssignRoleToUserUseCase;
import com.instagram.domain.port.in.rbac.GetUserPermissionsUseCase;
import com.instagram.domain.port.in.rbac.RevokeRoleFromUserUseCase;
import com.instagram.domain.port.in.rbac.UpdateRolePermissionsUseCase;
import com.instagram.domain.port.out.AuditLogRepository;
import com.instagram.domain.port.out.PermissionRepository;
import com.instagram.domain.port.out.RoleRepository;

@ExtendWith(MockitoExtension.class)
public class RbacServiceTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PermissionRepository permissionRepository;
    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private RbacService rbacService;

    UUID actorId;
    UUID targetId;

    @BeforeEach
    void setUp() {
        actorId = UUID.randomUUID();
        targetId = UUID.randomUUID();
    }

    private Role buildRole(RoleName name) {
        return Role.builder()
                .id(UUID.randomUUID())
                .name(name)
                .system(true)
                .build();
    }

    @Test
    void assignRoleToUser_whenSuperAdminAssignsRole_persistsAndLogsAudit() {
        Role moderatorRole = buildRole(RoleName.MODERATOR);
        when(roleRepository.findByName(RoleName.MODERATOR)).thenReturn(Optional.of(moderatorRole));
        when(roleRepository.findRolesByUserId(actorId)).thenReturn(Set.of(buildRole(RoleName.SUPER_ADMIN)));
        when(roleRepository.userHasRole(targetId, RoleName.MODERATOR)).thenReturn(false);
        when(roleRepository.findRolesByUserId(targetId)).thenReturn(Set.of(moderatorRole));

        rbacService.assignRoleToUser(new AssignRoleToUserUseCase.Command(actorId, targetId, RoleName.MODERATOR));

        verify(roleRepository).assignRoleToUser(eq(targetId), eq(moderatorRole.getId()), eq(actorId));

        ArgumentCaptor<String> actionCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditLogRepository).log(eq(actorId), actionCaptor.capture(), any(), any(), any(), any());
        assertEquals(AuditLogRepository.ROLE_ASSIGNED, actionCaptor.getValue());
    }

    @Test
    void assignRoleToUser_whenAdminAssignsAdminRole_throwsInsufficientPrivilege() {
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(buildRole(RoleName.ADMIN)));
        when(roleRepository.findRolesByUserId(actorId)).thenReturn(Set.of(buildRole(RoleName.ADMIN)));

        assertThrows(InsufficientPrivilegeException.class, () ->
                rbacService.assignRoleToUser(
                        new AssignRoleToUserUseCase.Command(actorId, targetId, RoleName.ADMIN)));
    }

    @Test
    void assignRoleToUser_whenRoleAlreadyAssigned_throwsRoleAlreadyAssigned() {
        Role moderatorRole = buildRole(RoleName.MODERATOR);
        when(roleRepository.findByName(RoleName.MODERATOR)).thenReturn(Optional.of(moderatorRole));
        when(roleRepository.findRolesByUserId(actorId)).thenReturn(Set.of(buildRole(RoleName.SUPER_ADMIN)));
        when(roleRepository.userHasRole(targetId, RoleName.MODERATOR)).thenReturn(true);

        assertThrows(RoleAlreadyAssignedException.class, () ->
                rbacService.assignRoleToUser(
                        new AssignRoleToUserUseCase.Command(actorId, targetId, RoleName.MODERATOR)));
    }

    @Test
    void revokeRoleFromUser_whenLastSuperAdmin_throwsInsufficientPrivilege() {
        Role superAdminRole = buildRole(RoleName.SUPER_ADMIN);
        when(roleRepository.findByName(RoleName.SUPER_ADMIN)).thenReturn(Optional.of(superAdminRole));
        when(roleRepository.findRolesByUserId(actorId)).thenReturn(Set.of(superAdminRole));
        when(roleRepository.userHasRole(targetId, RoleName.SUPER_ADMIN)).thenReturn(true);
        when(roleRepository.countUsersWithRole(RoleName.SUPER_ADMIN)).thenReturn(1L);

        assertThrows(InsufficientPrivilegeException.class, () ->
                rbacService.revokeRoleFromUser(
                        new RevokeRoleFromUserUseCase.Command(actorId, targetId, RoleName.SUPER_ADMIN)));
    }

    @Test
    void updateRolePermissions_whenRemovingRolePermissionManageFromSuperAdmin_throwsProtectedRoleException() {
        when(roleRepository.findRolesByUserId(actorId)).thenReturn(Set.of(buildRole(RoleName.SUPER_ADMIN)));

        Set<PermissionName> permissionsWithoutManage = Set.of(PermissionName.REPORT_VIEW, PermissionName.ROLE_VIEW);
        assertThrows(ProtectedRoleException.class, () ->
                rbacService.updateRolePermissions(
                        new UpdateRolePermissionsUseCase.Command(actorId, RoleName.SUPER_ADMIN, permissionsWithoutManage)));
    }

    @Test
    void updateRolePermissions_whenActorIsNotSuperAdmin_throwsInsufficientPrivilege() {
        when(roleRepository.findRolesByUserId(actorId)).thenReturn(Set.of(buildRole(RoleName.ADMIN)));

        Set<PermissionName> permissions = Set.of(PermissionName.REPORT_VIEW, PermissionName.ROLE_PERMISSION_MANAGE);
        assertThrows(InsufficientPrivilegeException.class, () ->
                rbacService.updateRolePermissions(
                        new UpdateRolePermissionsUseCase.Command(actorId, RoleName.MODERATOR, permissions)));
    }

    @Test
    void assignDefaultRole_whenUserAlreadyHasUserRole_doesNotAssignAgain() {
        when(roleRepository.findByName(RoleName.USER)).thenReturn(Optional.of(buildRole(RoleName.USER)));
        when(roleRepository.userHasRole(targetId, RoleName.USER)).thenReturn(true);

        rbacService.assignDefaultRole(new AssignDefaultRoleUseCase.Command(targetId));

        verify(roleRepository, never()).assignRoleToUser(any(), any(), any());
    }

    @Test
    void getUserPermissions_whenRepositoryReturnsNull_returnsEmptySet() {
        when(roleRepository.findPermissionNamesByUserId(targetId)).thenReturn(null);

        Set<PermissionName> result = rbacService.getUserPermissions(
                new GetUserPermissionsUseCase.Query(targetId));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
