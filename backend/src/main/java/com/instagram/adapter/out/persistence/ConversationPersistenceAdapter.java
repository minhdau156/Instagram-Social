package com.instagram.adapter.out.persistence;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.ConversationJpaEntity;
import com.instagram.adapter.out.persistence.entity.ConversationMemberId;
import com.instagram.adapter.out.persistence.entity.ConversationMemberJpaEntity;
import com.instagram.adapter.out.persistence.repository.ConversationJpaRepository;
import com.instagram.adapter.out.persistence.repository.ConversationMemberJpaRepository;
import com.instagram.domain.model.Conversation;
import com.instagram.domain.model.ConversationMember;
import com.instagram.domain.model.ConversationMember.Role;
import com.instagram.domain.port.out.ConversationRepository;

@Component
public class ConversationPersistenceAdapter implements ConversationRepository {

    private final ConversationJpaRepository conversationJpaRepository;
    private final ConversationMemberJpaRepository conversationMemberJpaRepository;

    public ConversationPersistenceAdapter(ConversationJpaRepository conversationJpaRepository,
            ConversationMemberJpaRepository conversationMemberJpaRepository) {
        this.conversationJpaRepository = conversationJpaRepository;
        this.conversationMemberJpaRepository = conversationMemberJpaRepository;
    }

    @Override
    public Conversation save(Conversation conversation) {
        var conversationEntity = toEntity(conversation);
        var saved = conversationJpaRepository.save(conversationEntity);
        return toDomain(saved);
    }

    @Override
    public Optional<Conversation> findById(UUID conversationId) {
        return conversationJpaRepository.findById(conversationId).map(this::toDomain);
    }

    @Override
    public List<Conversation> findByMemberId(UUID userId, Pageable pageable) {
        return conversationJpaRepository.findByMemberId(userId, pageable).map(this::toDomain).toList();
    }

    @Override
    public void addMember(UUID conversationId, UUID userId, Role role) {
        ConversationMemberId id = new ConversationMemberId(conversationId, userId);
        ConversationMemberJpaEntity memberJpaEntity = ConversationMemberJpaEntity.builder()
                .id(id)
                .isAdmin(role == Role.OWNER)
                .build();
        conversationMemberJpaRepository.save(memberJpaEntity);
    }

    @Override
    public void removeMember(UUID conversationId, UUID userId) {
        conversationMemberJpaRepository.deleteById(new ConversationMemberId(conversationId, userId));
    }

    @Override
    public boolean isMember(UUID conversationId, UUID userId) {
        return conversationMemberJpaRepository.existsByIdConversationIdAndIdUserId(conversationId, userId);
    }

    @Override
    public Optional<Conversation> findExisting1to1(UUID userId1, UUID userId2) {
        return conversationJpaRepository.findExisting1to1(userId1, userId2).map(this::toDomain);
    }

    @Override
    public Optional<ConversationMember> findMember(UUID conversationId, UUID userId) {
        return conversationMemberJpaRepository
                .findByIdConversationIdAndIdUserId(conversationId, userId)
                .map(this::memberToDomain);
    }

    @Override
    public List<UUID> findMemberIds(UUID conversationId) {
        return conversationMemberJpaRepository.findUserIdsByConversationId(conversationId);
    }

    private ConversationJpaEntity toEntity(Conversation conversation) {
        ConversationJpaEntity entity = ConversationJpaEntity.builder()
                .id(conversation.getId())
                .name(conversation.getName())
                .isGroup(conversation.getIsGroup())
                .avatarUrl(conversation.getPictureUrl())
                .createdById(conversation.getCreatedBy())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();

        return entity;
    }

    private Conversation toDomain(ConversationJpaEntity conversationJpaEntity) {
        return Conversation.builder()
                .id(conversationJpaEntity.getId())
                .name(conversationJpaEntity.getName())
                .isGroup(conversationJpaEntity.isGroup())
                .pictureUrl(conversationJpaEntity.getAvatarUrl())
                .createdById(conversationJpaEntity.getCreatedById())
                .createdAt(conversationJpaEntity.getCreatedAt())
                .updatedAt(conversationJpaEntity.getUpdatedAt())
                .build();
    }

    private ConversationMember memberToDomain(ConversationMemberJpaEntity memberJpaEntity) {
        return ConversationMember.builder()
                .conversationId(memberJpaEntity.getId().getConversationId())
                .userId(memberJpaEntity.getId().getUserId())
                .role(memberJpaEntity.isAdmin() ? ConversationMember.Role.OWNER : ConversationMember.Role.MEMBER)
                .joinedAt(memberJpaEntity.getJoinedAt())
                .lastReadAt(memberJpaEntity.getLastReadAt())
                .leftAt(memberJpaEntity.getLeftAt())
                .build();
    }

    @Override
    public Map<UUID, List<UUID>> findMemberIdsByConversationIds(List<UUID> conversationIds) {
        List<Object[]> result = conversationMemberJpaRepository.findUserIdsByConversationIds(conversationIds);
        Map<UUID, List<UUID>> memberIdsByConversationId = new HashMap<>();
        for (Object[] row : result) {
            UUID conversationId = (UUID) row[0];
            UUID userId = (UUID) row[1];
            memberIdsByConversationId.computeIfAbsent(conversationId, k -> new ArrayList<>()).add(userId);
        }
        return memberIdsByConversationId;
    }

}
