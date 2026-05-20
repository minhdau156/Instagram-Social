package com.instagram.domain.port.in.user;

import java.util.UUID;
import java.util.Collection;
import java.util.List;

import com.instagram.domain.model.UserStats;

public interface GetUserStatsUseCase {
    UserStats getUserStats(UUID userId);

    List<UserStats> findAllUserStatByIds(Collection<UUID> userIds);
}
