package com.instagram.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.instagram.domain.model.Conversation;
import com.instagram.domain.model.ConversationMember;

public interface ConversationRepository {
    Conversation save(Conversation conversation);

    Optional<Conversation> findById(UUID conversationId);

    List<Conversation> findByMemberId(UUID userId, Pageable pageable);

    void addMember(UUID conversationId, UUID userId, ConversationMember.Role role);

    void removeMember(UUID conversationId, UUID userId);

    boolean isMember(UUID conversationId, UUID userId);

    Optional<Conversation> findExisting1to1(UUID userId1, UUID userId2);

}
