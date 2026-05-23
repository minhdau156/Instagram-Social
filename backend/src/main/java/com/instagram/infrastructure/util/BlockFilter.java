package com.instagram.infrastructure.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.instagram.domain.port.out.ModerationRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BlockFilter {

    private final ModerationRepository moderationRepository;

    // No request-level caching — caching is a Phase 10 concern.
    public Set<UUID> getExcludedUserIds(UUID currentUserId) {
        List<UUID> blocked = moderationRepository.findBlockedUserIdsByBlockerId(currentUserId);
        List<UUID> blockers = moderationRepository.findBlockerIdsByBlockedId(currentUserId);
        if (blocked.isEmpty() && blockers.isEmpty()) {
            return Collections.emptySet();
        }
        Set<UUID> excludedIds = new HashSet<>(blocked);
        excludedIds.addAll(blockers);
        return excludedIds;
    }
}
