package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.adapter.out.persistence.entity.UserRoleId;
import com.instagram.adapter.out.persistence.entity.UserRoleJpaEntity;

public interface UserRoleJpaRepository extends JpaRepository<UserRoleJpaEntity, UserRoleId> {

    List<UserRoleJpaEntity> findByIdUserId(UUID userId);

    boolean existsByIdUserIdAndIdRoleId(UUID userId, UUID roleId);

    @Modifying
    @Transactional
    @Query("DELETE FROM UserRoleJpaEntity ur WHERE ur.id.userId = :userId AND ur.id.roleId = :roleId")
    void deleteByUserIdAndRoleId(@Param("userId") UUID userId, @Param("roleId") UUID roleId);

    long countByIdRoleId(UUID roleId);

    @Query("SELECT p.name FROM UserRoleJpaEntity ur " +
           "JOIN RoleJpaEntity r ON r.id = ur.id.roleId " +
           "JOIN r.permissions p " +
           "WHERE ur.id.userId = :userId")
    Set<String> findPermissionNamesByUserId(@Param("userId") UUID userId);
}
