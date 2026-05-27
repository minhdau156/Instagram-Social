package com.instagram.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.instagram.adapter.out.persistence.entity.PermissionJpaEntity;
import com.instagram.adapter.out.persistence.entity.RoleJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserRoleId;
import com.instagram.adapter.out.persistence.entity.UserRoleJpaEntity;
import com.instagram.adapter.out.persistence.repository.PermissionJpaRepository;
import com.instagram.adapter.out.persistence.repository.RoleJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserRoleJpaRepository;
import com.instagram.domain.model.PermissionName;
import com.instagram.domain.model.Role;
import com.instagram.domain.model.RoleName;
import com.instagram.infrastructure.config.JpaConfig;

@DataJpaTest
@Import(JpaConfig.class)
@TestPropertySource(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop" })
public class RbacPersistenceAdapterIT {

    @Autowired
    private RoleJpaRepository roleJpaRepository;
    @Autowired
    private PermissionJpaRepository permissionJpaRepository;
    @Autowired
    private UserRoleJpaRepository userRoleJpaRepository;

    RbacPersistenceAdapter rbacPersistenceAdapter;

    PermissionJpaEntity reportViewPerm;
    PermissionJpaEntity reportReviewPerm;
    RoleJpaEntity moderatorRole;

    @BeforeEach
    void setUp() {
        rbacPersistenceAdapter = new RbacPersistenceAdapter(
                roleJpaRepository, permissionJpaRepository, userRoleJpaRepository);

        reportViewPerm = permissionJpaRepository.save(
                PermissionJpaEntity.builder().name("REPORT_VIEW").description("View reports").build());
        reportReviewPerm = permissionJpaRepository.save(
                PermissionJpaEntity.builder().name("REPORT_REVIEW").description("Review reports").build());

        moderatorRole = roleJpaRepository.save(
                RoleJpaEntity.builder()
                        .name("MODERATOR")
                        .description("Moderator")
                        .system(true)
                        .permissions(Set.of(reportViewPerm, reportReviewPerm))
                        .build());
    }

    @Test
    void findRolesByUserId_whenUserHasRole_returnsRolesWithPermissionsPopulated() {
        UUID userId = UUID.randomUUID();
        userRoleJpaRepository.save(UserRoleJpaEntity.builder()
                .id(new UserRoleId(userId, moderatorRole.getId()))
                .build());

        Set<Role> roles = rbacPersistenceAdapter.findRolesByUserId(userId);

        assertEquals(1, roles.size());
        Role role = roles.iterator().next();
        assertEquals(RoleName.MODERATOR, role.getName());
        assertEquals(2, role.getPermissions().size());
    }

    @Test
    void findPermissionNamesByUserId_returnsFlattenedDeduplicatedSet() {
        UUID userId = UUID.randomUUID();
        userRoleJpaRepository.save(UserRoleJpaEntity.builder()
                .id(new UserRoleId(userId, moderatorRole.getId()))
                .build());

        Set<PermissionName> permissions = rbacPersistenceAdapter.findPermissionNamesByUserId(userId);

        assertNotNull(permissions);
        assertEquals(2, permissions.size());
        assertTrue(permissions.contains(PermissionName.REPORT_VIEW));
        assertTrue(permissions.contains(PermissionName.REPORT_REVIEW));
    }

    @Test
    void assignRoleToUser_thenUserHasRole_isTrue_afterRevoke_isFalse() {
        UUID userId = UUID.randomUUID();

        rbacPersistenceAdapter.assignRoleToUser(userId, moderatorRole.getId(), null);
        assertTrue(rbacPersistenceAdapter.userHasRole(userId, RoleName.MODERATOR));

        rbacPersistenceAdapter.revokeRoleFromUser(userId, moderatorRole.getId());
        assertFalse(rbacPersistenceAdapter.userHasRole(userId, RoleName.MODERATOR));
    }

    @Test
    void replaceRolePermissions_replacesOldPermissionsWithNew() {
        PermissionJpaEntity roleAssignPerm = permissionJpaRepository.save(
                PermissionJpaEntity.builder().name("ROLE_ASSIGN").description("Assign roles").build());

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
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();

        assertEquals(0, rbacPersistenceAdapter.countUsersWithRole(RoleName.MODERATOR));

        rbacPersistenceAdapter.assignRoleToUser(user1, moderatorRole.getId(), null);
        rbacPersistenceAdapter.assignRoleToUser(user2, moderatorRole.getId(), null);

        assertEquals(2, rbacPersistenceAdapter.countUsersWithRole(RoleName.MODERATOR));
    }
}
