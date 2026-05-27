package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.instagram.adapter.out.persistence.entity.RoleJpaEntity;

public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, UUID> {

    @EntityGraph(attributePaths = "permissions")
    Optional<RoleJpaEntity> findByName(String name);

    @EntityGraph(attributePaths = "permissions")
    @Query("SELECT r FROM RoleJpaEntity r")
    List<RoleJpaEntity> findAllWithPermissions();

    @EntityGraph(attributePaths = "permissions")
    @Query("SELECT r FROM RoleJpaEntity r WHERE r.id IN (SELECT ur.id.roleId FROM UserRoleJpaEntity ur WHERE ur.id.userId = :userId)")
    List<RoleJpaEntity> findByUserId(@Param("userId") UUID userId);
}
