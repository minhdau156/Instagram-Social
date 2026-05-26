package com.instagram.adapter.out.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.instagram.adapter.out.persistence.entity.PermissionJpaEntity;

public interface PermissionJpaRepository extends JpaRepository<PermissionJpaEntity, UUID> {

    Optional<PermissionJpaEntity> findByName(String name);

    List<PermissionJpaEntity> findByIdIn(Collection<UUID> ids);
}
