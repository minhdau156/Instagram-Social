package com.instagram.domain.port.in.moderation;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.UserBlock;

public interface GetBlockedUsersUseCase {
    List<UserBlock> getBlockedUsers(Query query);

    record Query(
            UUID userId,
            int page,
            int size) {
    }
}
