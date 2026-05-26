package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.instagram.adapter.out.persistence.entity.RoleJpaEntity;

public interface RoleJpaRepository extends JpaRepository<RoleJpaEntity, UUID> {

    @EntityGraph(attributePaths = "permissions")
    Optional<RoleJpaEntity> findByName(String name);

    @EntityGraph(attributePaths = "permissions")
    @Query("SELECT r FROM RoleJpaEntity r")
    List<RoleJpaEntity> findAllWithPermissions();
}
