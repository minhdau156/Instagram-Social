package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.instagram.adapter.out.persistence.entity.DeviceTokenJpaEntity;

public interface DeviceTokenJpaRepository extends JpaRepository<DeviceTokenJpaEntity, UUID> {

    List<DeviceTokenJpaEntity> findByUserId(UUID userId);

    void deleteByToken(String token);
}
