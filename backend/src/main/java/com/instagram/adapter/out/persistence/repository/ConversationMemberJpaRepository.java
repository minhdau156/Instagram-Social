package com.instagram.adapter.out.persistence.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.instagram.adapter.out.persistence.entity.ConversationMemberJpaEntity;
import com.instagram.adapter.out.persistence.entity.ConversationMemberId;

public interface ConversationMemberJpaRepository
        extends JpaRepository<ConversationMemberJpaEntity, ConversationMemberId> {

    boolean existsByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);

    Optional<ConversationMemberJpaEntity> findByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);

}
