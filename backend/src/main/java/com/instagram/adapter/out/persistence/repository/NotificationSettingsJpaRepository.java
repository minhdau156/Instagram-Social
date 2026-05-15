package com.instagram.adapter.out.persistence.repository;

import com.instagram.adapter.out.persistence.entity.NotificationSettingsJpaEntity;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSettingsJpaRepository extends JpaRepository<NotificationSettingsJpaEntity, UUID> {
}
