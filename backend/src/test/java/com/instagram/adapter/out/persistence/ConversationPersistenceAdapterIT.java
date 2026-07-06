package com.instagram.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import com.instagram.adapter.out.persistence.entity.ConversationMemberId;
import com.instagram.adapter.out.persistence.entity.ConversationMemberJpaEntity;
import com.instagram.adapter.out.persistence.repository.ConversationJpaRepository;
import com.instagram.adapter.out.persistence.repository.ConversationMemberJpaRepository;
import com.instagram.domain.model.Conversation;
import com.instagram.domain.model.ConversationMember;
import com.instagram.infrastructure.config.JpaConfig;

@DataJpaTest
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class ConversationPersistenceAdapterIT {
    @Autowired
    private ConversationJpaRepository conversationJpaRepository;
    @Autowired
    private ConversationMemberJpaRepository conversationMemberJpaRepository;

    ConversationPersistenceAdapter conversationPersistenceAdapter;

    Conversation conversation;
    ConversationMember member;

    @BeforeEach
    void setUp() {
        conversationPersistenceAdapter = new ConversationPersistenceAdapter(conversationJpaRepository,
                conversationMemberJpaRepository);
        conversation = Conversation.builder()
                .id(UUID.randomUUID())
                .name("Conversation 1")
                .isGroup(false)
                .pictureUrl("https://example.com/image.jpg")
                .createdById(UUID.randomUUID())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        member = ConversationMember.builder()
                .conversationId(conversation.getId())
                .userId(UUID.randomUUID())
                .role(ConversationMember.Role.OWNER)
                .joinedAt(OffsetDateTime.now())
                .build();
    }

    @Test
    void save_isSuccess_shouldSave() {
        // given

        // when
        Conversation savedConversation = conversationPersistenceAdapter.save(conversation);

        // then

        assertEquals(conversation.getName(), savedConversation.getName());
        assertEquals(conversation.getIsGroup(), savedConversation.getIsGroup());
        assertEquals(conversation.getPictureUrl(), savedConversation.getPictureUrl());
        assertEquals(conversation.getCreatedBy(), savedConversation.getCreatedBy());
        assertEquals(conversation.getCreatedAt(), savedConversation.getCreatedAt());
        assertEquals(conversation.getUpdatedAt(), savedConversation.getUpdatedAt());

    }

    @Test
    void findById_isSuccess_shouldFind() {
        // given
        Conversation savedConversation = conversationPersistenceAdapter.save(conversation);

        // when
        Optional<Conversation> foundConversation = conversationPersistenceAdapter.findById(savedConversation.getId());

        // then
        assertEquals(savedConversation.getId(), foundConversation.get().getId());
        assertEquals(savedConversation.getName(), foundConversation.get().getName());
        assertEquals(savedConversation.getIsGroup(), foundConversation.get().getIsGroup());
        assertEquals(savedConversation.getPictureUrl(), foundConversation.get().getPictureUrl());
        assertEquals(savedConversation.getCreatedBy(), foundConversation.get().getCreatedBy());
        assertEquals(savedConversation.getCreatedAt(), foundConversation.get().getCreatedAt());
        assertEquals(savedConversation.getUpdatedAt(), foundConversation.get().getUpdatedAt());

    }

    @Test
    void findById_isFail_shouldNotFind() {
        // given
        UUID conversationId = UUID.randomUUID();

        // when
        Optional<Conversation> foundConversation = conversationPersistenceAdapter.findById(conversationId);

        // then
        assertEquals(Optional.empty(), foundConversation);
    }

    @Test
    void findByMemberId_isSuccess_shouldFind() {
        // given

        // when
        Conversation savedConversation = conversationPersistenceAdapter.save(conversation);
        ConversationMemberJpaEntity entity = ConversationMemberJpaEntity.builder()
                .id(new ConversationMemberId(savedConversation.getId(), member.getUserId()))
                .isAdmin(member.getRole() == ConversationMember.Role.OWNER)
                .joinedAt(member.getJoinedAt())
                .build();
        ConversationMemberJpaEntity savedMember = conversationMemberJpaRepository.save(entity);
        List<Conversation> foundConversation = conversationPersistenceAdapter.findByMemberId(
                savedMember.getId().getUserId(),
                Pageable.unpaged());

        // then
        assertEquals(savedConversation.getId(), foundConversation.get(0).getId());
        assertEquals(savedConversation.getName(), foundConversation.get(0).getName());
        assertEquals(savedConversation.getIsGroup(), foundConversation.get(0).getIsGroup());
        assertEquals(savedConversation.getPictureUrl(), foundConversation.get(0).getPictureUrl());
        assertEquals(savedConversation.getCreatedBy(), foundConversation.get(0).getCreatedBy());

    }

    @Test
    void addMember_isSuccess_shouldAddMember() {
        // given

        // when
        Conversation savedConversation = conversationPersistenceAdapter.save(conversation);
        conversationPersistenceAdapter.addMember(savedConversation.getId(), member.getUserId(), member.getRole());
        Optional<ConversationMember> foundMember = conversationPersistenceAdapter.findMember(savedConversation.getId(),
                member.getUserId());

        // then
        assertEquals(savedConversation.getId(), foundMember.get().getConversationId());
        assertEquals(member.getUserId(), foundMember.get().getUserId());
        assertEquals(member.getRole(), foundMember.get().getRole());
        assertTrue(conversationPersistenceAdapter.isMember(savedConversation.getId(), member.getUserId()));
    }

    @Test
    void removeMember_isSuccess_shouldRemoveMember() {
        // given
        Conversation savedConversation = conversationPersistenceAdapter.save(conversation);
        conversationPersistenceAdapter.addMember(savedConversation.getId(), member.getUserId(), member.getRole());

        // when
        conversationPersistenceAdapter.removeMember(savedConversation.getId(), member.getUserId());
        Optional<ConversationMember> foundMember = conversationPersistenceAdapter.findMember(savedConversation.getId(),
                member.getUserId());

        // then
        assertEquals(Optional.empty(), foundMember);
        assertFalse(conversationPersistenceAdapter.isMember(savedConversation.getId(), member.getUserId()));
    }

    @Test
    void findExisting1to1_isSuccess_shouldFind() {
        // given
        UUID user1Id = UUID.randomUUID();
        UUID user2Id = UUID.randomUUID();
        Conversation savedConversation = conversationPersistenceAdapter.save(conversation);
        conversationPersistenceAdapter.addMember(savedConversation.getId(), user1Id, ConversationMember.Role.OWNER);
        conversationPersistenceAdapter.addMember(savedConversation.getId(), user2Id, ConversationMember.Role.MEMBER);

        // when
        Optional<Conversation> found = conversationPersistenceAdapter.findExisting1to1(user1Id, user2Id);

        // then
        assertTrue(found.isPresent());
        assertEquals(savedConversation.getId(), found.get().getId());
    }

    @Test
    void findMember_isSuccess_shouldFindMember() {
        // given
        Conversation savedConversation = conversationPersistenceAdapter.save(conversation);
        conversationPersistenceAdapter.addMember(savedConversation.getId(), member.getUserId(), member.getRole());

        // when
        Optional<ConversationMember> foundMember = conversationPersistenceAdapter.findMember(savedConversation.getId(),
                member.getUserId());

        // then
        assertEquals(savedConversation.getId(), foundMember.get().getConversationId());
        assertEquals(member.getUserId(), foundMember.get().getUserId());
        assertEquals(member.getRole(), foundMember.get().getRole());
    }

    @Test
    void findMemberIds_isSuccess_shouldReturnMemberIds() {
        conversationPersistenceAdapter.save(conversation);
        UUID user1Id = UUID.randomUUID();
        conversationPersistenceAdapter.addMember(conversation.getId(), user1Id, ConversationMember.Role.MEMBER);

        List<UUID> result = conversationPersistenceAdapter.findMemberIds(conversation.getId());

        assertTrue(result.contains(user1Id));
    }

}
