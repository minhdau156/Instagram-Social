package com.instagram.domain.port.in.follow;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.UserSummary;

public interface GetFollowersUseCase {
    FollowersPage getFollowers(Query query);

    record Query(String targetUsername, UUID currentUserId, String cursor, int size) {
    }

    record FollowersPage(List<UserSummary> items, String nextCursor) {
    }
}
