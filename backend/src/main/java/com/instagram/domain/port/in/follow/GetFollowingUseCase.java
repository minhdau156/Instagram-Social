package com.instagram.domain.port.in.follow;

import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.UserSummary;

public interface GetFollowingUseCase {
    FollowingPage getFollowing(Query query);

    record Query(String targetUsername, UUID currentUserId, String cursor, int size) {
    }

    record FollowingPage(List<UserSummary> items, String nextCursor) {
    }
}
