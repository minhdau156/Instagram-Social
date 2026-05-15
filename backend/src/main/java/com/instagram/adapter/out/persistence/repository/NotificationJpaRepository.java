package com.instagram.adapter.out.persistence.repository;

import com.instagram.adapter.out.persistence.entity.NotificationJpaEntity;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {
    Page<NotificationJpaEntity> findByRecipientId(UUID recipientId, Pageable pageable);

    @Modifying
    @Query("UPDATE NotificationJpaEntity n SET n.isRead = true WHERE n.id = :id")
    void markAsRead(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE NotificationJpaEntity n SET n.isRead = true WHERE n.recipientId = :recipientId")
    void markAllAsRead(@Param("recipientId") UUID recipientId);

    long countByRecipientIdAndIsReadFalse(UUID recipientId);
}