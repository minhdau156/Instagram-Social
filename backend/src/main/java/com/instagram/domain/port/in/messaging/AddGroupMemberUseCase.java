package com.instagram.domain.port.in.messaging;

import java.util.List;
import java.util.UUID;

public interface AddGroupMemberUseCase {
    void addGroupMember(Command command);

    record Command(UUID conversationId, UUID requesterId, List<UUID> newMemberIds) {
    }
}
