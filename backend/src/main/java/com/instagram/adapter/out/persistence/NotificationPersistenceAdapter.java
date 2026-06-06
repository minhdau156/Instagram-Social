package com.instagram.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.NotificationJpaEntity;
import com.instagram.adapter.out.persistence.repository.NotificationJpaRepository;
import com.instagram.domain.model.Notification;
import com.instagram.domain.port.out.NotificationRepository;

@Component
public class NotificationPersistenceAdapter implements NotificationRepository {

    private final NotificationJpaRepository jpaRepository;

    public NotificationPersistenceAdapter(NotificationJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Notification save(Notification notification) {
        NotificationJpaEntity saved = this.jpaRepository.save(toEntity(notification));
        return toDomain(saved);
    }

    @Override
    public List<Notification> findByRecipientId(UUID recipientId, String cursorTs, UUID cursorId, int size) {
        return this.jpaRepository.findByRecipientIdKeysetBefore(recipientId, cursorTs, cursorId, size)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Notification> findById(UUID notificationId) {
        return this.jpaRepository.findById(notificationId).map(this::toDomain);
    }

    @Override
    public void markAsRead(UUID notificationId) {
        jpaRepository.markAsRead(notificationId);
    }

    @Override
    public void markAllAsRead(UUID recipientId) {
        jpaRepository.markAllAsRead(recipientId);
    }

    @Override
    public long getUnreadCount(UUID recipientId) {
        return jpaRepository.countByRecipientIdAndIsReadFalse(recipientId);

    }

    private Notification toDomain(NotificationJpaEntity e) {
        String entityTypeString = e.getEntityType();
        Notification.EntityType entityType = Notification.EntityType.valueOf(entityTypeString.toUpperCase());
        return Notification.builder()
                .id(e.getId())
                .type(e.getType())
                .entityId(e.getEntityId())
                .entityType(entityType)
                .actorId(e.getActorId())
                .recipientId(e.getRecipientId())
                .isRead(e.isRead())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private NotificationJpaEntity toEntity(Notification n) {

        return NotificationJpaEntity.builder()
                .id(n.getId())
                .type(n.getType())
                .entityId(n.getEntityId())
                .entityType(n.getEntityType().name())
                .actorId(n.getActorId())
                .recipientId(n.getRecipientId())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }

}
