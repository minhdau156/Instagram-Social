package com.instagram.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.*;

import com.instagram.domain.exception.*;
import com.instagram.domain.model.Permission;
import com.instagram.domain.port.in.rbac.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.Role;
import com.instagram.domain.model.RoleName;
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

    private Permission buildPermission(PermissionName name) {
        return Permission.builder()
                .id(UUID.randomUUID())
                .name(name)
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
    void revokeRoleFromUser_isSuperAdmin_revokeRoke() {
        Role superAdminRole = buildRole(RoleName.SUPER_ADMIN);
        Role adminRole = buildRole(RoleName.ADMIN);
        when(roleRepository.findByName(RoleName.ADMIN)).thenReturn(Optional.of(adminRole));
        when(roleRepository.findRolesByUserId(actorId)).thenReturn(Set.of(superAdminRole));
        when(roleRepository.userHasRole(targetId, RoleName.ADMIN)).thenReturn(true);
//        when(roleRepository.countUsersWithRole(RoleName.SUPER_ADMIN)).thenReturn(2L);

        doNothing().when(roleRepository).revokeRoleFromUser(any(), any());
        doNothing().when(auditLogRepository).log(any(), any(), any(), any(), any(), any());

        RevokeRoleFromUserUseCase.Command command = new RevokeRoleFromUserUseCase.Command(actorId, targetId, RoleName.ADMIN);

        rbacService.revokeRoleFromUser(command);


    }

    @Test
    void revokeRoleFromUser_whenRoleIsNotExist_throwException() {
        when(roleRepository.findByName(any())).thenReturn(Optional.empty());

        assertThrows(RoleNotFoundException.class, () ->
                rbacService.revokeRoleFromUser(new RevokeRoleFromUserUseCase.Command(actorId, targetId, RoleName.ADMIN)));
    }

    @Test
    void revokeRoleFromUser_actorIsNotSuperAdmin_throwException() {
        Role moderateRole = buildRole(RoleName.MODERATOR);
        Role adminRole = buildRole(RoleName.ADMIN);
        when(roleRepository.findByName(any())).thenReturn(Optional.of(adminRole));
        when(roleRepository.findRolesByUserId(any())).thenReturn(Set.of(moderateRole));

        assertThrows(InsufficientPrivilegeException.class, () ->
                rbacService.revokeRoleFromUser(new RevokeRoleFromUserUseCase.Command(actorId, targetId, RoleName.ADMIN)));
    }

    @Test
    void revokeRoleFromUser_whenUserDoNotHaveRole_throwException() {
        Role superAdminRole = buildRole(RoleName.SUPER_ADMIN);
        Role adminRole = buildRole(RoleName.ADMIN);

        when(roleRepository.findByName(any())).thenReturn(Optional.of(adminRole));
        when(roleRepository.findRolesByUserId(any())).thenReturn(Set.of(superAdminRole));
        when(roleRepository.userHasRole(any(), any())).thenReturn(false);

        assertThrows(RoleNotAssignedException.class, () ->
                rbacService.revokeRoleFromUser(new RevokeRoleFromUserUseCase.Command(actorId, targetId, RoleName.ADMIN)));
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
    void listRoles_whenSuccess_shouldReturnListOfRoles() {
        Role userRole = buildRole(RoleName.USER);
        Role moderateRole = buildRole(RoleName.MODERATOR);
        Role superAdminRole = buildRole(RoleName.SUPER_ADMIN);
        Role adminRole = buildRole(RoleName.ADMIN);

        List<Role> roles = new ArrayList<>();
        roles.add(userRole);
        roles.add(moderateRole);
        roles.add(superAdminRole);
        roles.add(adminRole);

        when(roleRepository.findAllRoles()).thenReturn(roles);

        ListRolesUseCase.Query query = new ListRolesUseCase.Query();
        List<Role> result = rbacService.listRoles(query);

        assertEquals(4, result.size());
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

    @Test
    void getUserPermissions_whenSuccess_returnSetOfPermissionsName() {
        PermissionName permissionName = PermissionName.REPORT_VIEW;
        PermissionName permissionName2 = PermissionName.ROLE_VIEW;

        Set<PermissionName> permissions = Set.of(permissionName, permissionName2);

        when(roleRepository.findPermissionNamesByUserId(any())).thenReturn(permissions);

        Set<PermissionName> result = rbacService.getUserPermissions(new GetUserPermissionsUseCase.Query(targetId));

        assertEquals(2, result.size());
    }

    @Test
    void getUserRoles_whenSuccess_returnSetOfUserRoles() {
        Role userRole = buildRole(RoleName.USER);

        when(roleRepository.findRolesByUserId(any())).thenReturn(Set.of(userRole));

        Set<Role> role = rbacService.getUserRoles(new GetUserRolesUseCase.Query(targetId));

        assertNotNull(role);
        assertEquals(1, role.size());
    }

    @Test
    void updateRolePermissions_whenSuccess_theUpdateRolePermissions() {
        Role superAdminRole = buildRole(RoleName.SUPER_ADMIN);
        Permission permission = buildPermission(PermissionName.REPORT_VIEW);
        Role adminRole = buildRole(RoleName.ADMIN);

        when(roleRepository.findRolesByUserId(any())).thenReturn(Set.of(superAdminRole));
        when(permissionRepository.findByName(any())).thenReturn(Optional.of(permission));
        when(roleRepository.findByName(any())).thenReturn(Optional.of(adminRole));

        doNothing().when(roleRepository).replaceRolePermissions(any(), any());
        doNothing().when(auditLogRepository).log(any(), any(), any(), any(), any(), any());

        Set<PermissionName> permissionNames = new HashSet<>();
        permissionNames.add(PermissionName.REPORT_VIEW);

        UpdateRolePermissionsUseCase.Command command = new UpdateRolePermissionsUseCase.Command(actorId, RoleName.ADMIN, permissionNames);
        rbacService.updateRolePermissions(command);

    }

    @Test
    void updateRolePermissions_whenPermissionNotFound_throwException() {
        Role superAdminRole = buildRole(RoleName.SUPER_ADMIN);

        when(roleRepository.findRolesByUserId(any())).thenReturn(Set.of(superAdminRole));
        when(permissionRepository.findByName(any())).thenReturn(Optional.empty());

        Set<PermissionName> permissionNames = new HashSet<>();
        permissionNames.add(PermissionName.REPORT_VIEW);

        UpdateRolePermissionsUseCase.Command command = new UpdateRolePermissionsUseCase.Command(actorId, RoleName.ADMIN, permissionNames);

        assertThrows(IllegalArgumentException.class, () -> rbacService.updateRolePermissions(command));
    }

    @Test
    void updateRolePermissions_whenRoleNotFound_throwException() {
        Role superAdminRole = buildRole(RoleName.SUPER_ADMIN);
        Permission permission = buildPermission(PermissionName.REPORT_VIEW);

        when(roleRepository.findRolesByUserId(any())).thenReturn(Set.of(superAdminRole));
        when(permissionRepository.findByName(any())).thenReturn(Optional.of(permission));
        when(roleRepository.findByName(any())).thenReturn(Optional.empty());

        Set<PermissionName> permissionNames = new HashSet<>();
        permissionNames.add(PermissionName.REPORT_VIEW);

        UpdateRolePermissionsUseCase.Command command = new UpdateRolePermissionsUseCase.Command(actorId, RoleName.ADMIN, permissionNames);

        assertThrows(RoleNotFoundException.class, () -> rbacService.updateRolePermissions(command));
    }
}
