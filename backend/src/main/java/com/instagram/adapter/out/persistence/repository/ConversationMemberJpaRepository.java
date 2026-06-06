package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.instagram.adapter.out.persistence.entity.ConversationMemberJpaEntity;
import com.instagram.adapter.out.persistence.entity.ConversationMemberId;

public interface ConversationMemberJpaRepository
        extends JpaRepository<ConversationMemberJpaEntity, ConversationMemberId> {

    boolean existsByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);

    Optional<ConversationMemberJpaEntity> findByIdConversationIdAndIdUserId(UUID conversationId, UUID userId);

    @Query("SELECT m.id.userId FROM ConversationMemberJpaEntity m WHERE m.id.conversationId = :conversationId")
    List<UUID> findUserIdsByConversationId(@Param("conversationId") UUID conversationId);

    @Query(value = "SELECT conversation_id, user_id FROM conversation_members WHERE conversation_id IN :conversationIds", nativeQuery = true)
    List<Object[]> findUserIdsByConversationIds(@Param("conversationIds") List<UUID> conversationIds);

}
