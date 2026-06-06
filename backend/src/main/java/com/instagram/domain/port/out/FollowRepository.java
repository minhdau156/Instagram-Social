package com.instagram.domain.port.out;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.instagram.domain.model.Follow;
import com.instagram.domain.model.FollowStatus;

public interface FollowRepository {
    Follow save(Follow follow);

    void delete(UUID followerId, UUID followingId);

    Optional<Follow> findByFollowerIdAndFollowingId(UUID followerId, UUID followingId);

    List<Follow> findFollowersByUserId(UUID userId, Pageable pageable);

    List<Follow> findFollowingByUserId(UUID userId, Pageable pageable);

    List<Follow> findFollowersByUserIdKeyset(UUID userId, String cursorTs, UUID cursorId, int size);

    List<Follow> findFollowingByUserIdKeyset(UUID userId, String cursorTs, UUID cursorId, int size);

    /** Return all PENDING follow requests addressed to the given user. */
    List<Follow> findPendingRequestsByFollowingId(UUID followingId);

    long countFollowersByUserId(UUID userId);

    long countFollowingByUserId(UUID userId);

    Map<UUID, FollowStatus> findFollowStatusByFollowerIdAndFollowingIds(UUID followerId, List<UUID> followingIds);

}
