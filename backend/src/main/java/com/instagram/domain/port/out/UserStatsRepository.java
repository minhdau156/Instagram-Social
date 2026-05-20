package com.instagram.domain.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.instagram.domain.model.UserStats;

public interface UserStatsRepository {
    void create(UserStats userStats);

    Optional<UserStats> findByUserId(UUID userId);

    List<UserStats> findAllByIds(Collection<UUID> userIds);

    void incrementFollowerCount(UUID userId);

    void decrementFollowerCount(UUID userId);

    void incrementFollowingCount(UUID userId);

    void decrementFollowingCount(UUID userId);
}
