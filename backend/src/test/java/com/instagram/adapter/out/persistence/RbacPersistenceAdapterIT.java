package com.instagram.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.*;

import com.instagram.domain.model.Permission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.instagram.adapter.out.persistence.entity.PermissionJpaEntity;
import com.instagram.adapter.out.persistence.entity.RoleJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserRoleId;
import com.instagram.adapter.out.persistence.entity.UserRoleJpaEntity;
import com.instagram.adapter.out.persistence.repository.PermissionJpaRepository;
import com.instagram.adapter.out.persistence.repository.RoleJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserRoleJpaRepository;
import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.Role;
import com.instagram.domain.model.RoleName;
import com.instagram.domain.model.UserStatus;

public class RbacPersistenceAdapterIT extends PostgresIntegrationTest {

    @Autowired
    private RoleJpaRepository roleJpaRepository;
    @Autowired
    private PermissionJpaRepository permissionJpaRepository;
    @Autowired
    private UserRoleJpaRepository userRoleJpaRepository;
    @Autowired
    private UserJpaRepository userJpaRepository;

    RbacPersistenceAdapter rbacPersistenceAdapter;

    // MODERATOR / REPORT_VIEW / REPORT_REVIEW come from the V4 Flyway seed data rather
    // than being inserted here — roles.name and permissions.name are UNIQUE, and
    // RoleName/PermissionName enums are a closed set, so tests must reuse the seeded
    // rows instead of creating duplicates.
    PermissionJpaEntity reportViewPerm;
    PermissionJpaEntity reportReviewPerm;
    RoleJpaEntity moderatorRole;

    @BeforeEach
    void setUp() {
        rbacPersistenceAdapter = new RbacPersistenceAdapter(
                roleJpaRepository, permissionJpaRepository, userRoleJpaRepository);

        reportViewPerm = permissionJpaRepository.findByName("REPORT_VIEW").orElseThrow();
        reportReviewPerm = permissionJpaRepository.findByName("REPORT_REVIEW").orElseThrow();
        moderatorRole = roleJpaRepository.findByName("MODERATOR").orElseThrow();
    }

    // user_roles.user_id is a real FK to users(id) — every userId used below must be a
    // persisted row, not a bare UUID.randomUUID().
    private UUID persistUser(String username) {
        return userJpaRepository.save(UserJpaEntity.builder()
                .username(username)
                .email(username + "@example.com")
                .fullName(username)
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build()).getId();
    }

    @Test
    void findRolesByUserId_whenUserHasRole_returnsRolesWithPermissionsPopulated() {
        UUID userId = persistUser("rbac_user1");
        userRoleJpaRepository.save(UserRoleJpaEntity.builder()
                .id(new UserRoleId(userId, moderatorRole.getId()))
                .build());

        Set<Role> roles = rbacPersistenceAdapter.findRolesByUserId(userId);

        assertEquals(1, roles.size());
        Role role = roles.iterator().next();
        assertEquals(RoleName.MODERATOR, role.getName());
        // Seeded MODERATOR role carries REPORT_VIEW, REPORT_REVIEW, CONTENT_MODERATE, USER_VIEW
        assertEquals(4, role.getPermissions().size());
        assertTrue(role.getPermissions().stream().anyMatch(p -> p.getName() == PermissionName.REPORT_VIEW));
        assertTrue(role.getPermissions().stream().anyMatch(p -> p.getName() == PermissionName.REPORT_REVIEW));
    }

    @Test
    void findPermissionNamesByUserId_returnsFlattenedDeduplicatedSet() {
        UUID userId = persistUser("rbac_user2");
        userRoleJpaRepository.save(UserRoleJpaEntity.builder()
                .id(new UserRoleId(userId, moderatorRole.getId()))
                .build());

        Set<PermissionName> permissions = rbacPersistenceAdapter.findPermissionNamesByUserId(userId);

        assertNotNull(permissions);
        assertEquals(4, permissions.size());
        assertTrue(permissions.contains(PermissionName.REPORT_VIEW));
        assertTrue(permissions.contains(PermissionName.REPORT_REVIEW));
    }

    @Test
    void assignRoleToUser_thenUserHasRole_isTrue_afterRevoke_isFalse() {
        UUID userId = persistUser("rbac_user3");

        rbacPersistenceAdapter.assignRoleToUser(userId, moderatorRole.getId(), null);
        assertTrue(rbacPersistenceAdapter.userHasRole(userId, RoleName.MODERATOR));

        rbacPersistenceAdapter.revokeRoleFromUser(userId, moderatorRole.getId());
        assertFalse(rbacPersistenceAdapter.userHasRole(userId, RoleName.MODERATOR));
    }

    @Test
    void replaceRolePermissions_replacesOldPermissionsWithNew() {
        PermissionJpaEntity roleAssignPerm = permissionJpaRepository.findByName("ROLE_ASSIGN").orElseThrow();

        rbacPersistenceAdapter.replaceRolePermissions(
                moderatorRole.getId(), Set.of(roleAssignPerm.getId()));

        List<Role> roles = rbacPersistenceAdapter.findAllRoles();
        Role updated = roles.stream()
                .filter(r -> r.getName() == RoleName.MODERATOR)
                .findFirst().orElseThrow();

        assertEquals(1, updated.getPermissions().size());
        assertEquals(PermissionName.ROLE_ASSIGN, updated.getPermissions().iterator().next().getName());
    }

    @Test
    void countUsersWithRole_reflectsAssignments() {
        UUID user1 = persistUser("rbac_user4");
        UUID user2 = persistUser("rbac_user5");

        assertEquals(0, rbacPersistenceAdapter.countUsersWithRole(RoleName.MODERATOR));

        rbacPersistenceAdapter.assignRoleToUser(user1, moderatorRole.getId(), null);
        rbacPersistenceAdapter.assignRoleToUser(user2, moderatorRole.getId(), null);

        assertEquals(2, rbacPersistenceAdapter.countUsersWithRole(RoleName.MODERATOR));
    }

    @Test
    void findByName_whenSuccess_returnsPermission() {
        Permission result = rbacPersistenceAdapter.findByName(PermissionName.REPORT_VIEW).get();
        assertNotNull(result);
    }

    @Test
    void findRoleByIds_whenSuccess_returnsRoles() {
        Collection<UUID> ids = Arrays.asList(reportReviewPerm.getId(), reportViewPerm.getId());

        Set<Permission> result = rbacPersistenceAdapter.findByIds(ids);

        assertEquals(2, result.size());
    }

    @Test
    void findRoleById_whenSuccess_returnsRole() {
        UUID roleId = moderatorRole.getId();

        Role role = rbacPersistenceAdapter.findById(roleId).get();

        assertNotNull(role);
    }

    @Test
    void findRoleByName_whenSuccess_returnsRole() {
        Role role = rbacPersistenceAdapter.findByName(RoleName.MODERATOR).get();

        assertNotNull(role);
    }

    @Test
    void findAllPermission_whenSuccess_returnsPermissions() {
        List<Permission> result = rbacPersistenceAdapter.findAllPermissions();

        // Seed data provisions exactly 10 permissions (V4 migration)
        assertEquals(10, result.size());
    }

}
