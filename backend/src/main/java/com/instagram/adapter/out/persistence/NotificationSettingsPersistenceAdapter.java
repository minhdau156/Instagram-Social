package com.instagram.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.NotificationSettingsJpaEntity;
import com.instagram.adapter.out.persistence.repository.NotificationSettingsJpaRepository;
import com.instagram.domain.model.NotificationSettings;
import com.instagram.domain.port.out.NotificationSettingsRepository;

@Component
public class NotificationSettingsPersistenceAdapter implements NotificationSettingsRepository {

    private final NotificationSettingsJpaRepository jpaRepository;

    public NotificationSettingsPersistenceAdapter(NotificationSettingsJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<NotificationSettings> findByUserId(UUID userId) {
        return this.jpaRepository.findById(userId).map(this::toDomain);
    }

    @Override
    public NotificationSettings save(NotificationSettings settings) {
        NotificationSettingsJpaEntity saved = this.jpaRepository.save(toEntity(settings));
        return toDomain(saved);
    }

    private NotificationSettings toDomain(NotificationSettingsJpaEntity e) {
        return new NotificationSettings(
                e.getUserId(),
                e.isLikesEnabled(),
                e.isCommentsEnabled(),
                e.isFollowsEnabled(),
                e.isMessagesEnabled(),
                e.isPushEnabled());

    }

    private NotificationSettingsJpaEntity toEntity(NotificationSettings s) {
        return NotificationSettingsJpaEntity.builder()
                .userId(s.userId())
                .likesEnabled(s.likesEnabled())
                .commentsEnabled(s.commentsEnabled())
                .followsEnabled(s.followsEnabled())
                .messagesEnabled(s.messagesEnabled())
                .pushEnabled(s.pushEnabled())
                .build();

    }

}