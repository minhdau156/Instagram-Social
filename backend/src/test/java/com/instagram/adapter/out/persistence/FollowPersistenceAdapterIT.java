package com.instagram.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.domain.model.Follow;
import com.instagram.domain.model.FollowStatus;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.UserStatus;
import com.instagram.infrastructure.config.JpaConfig;

@DataJpaTest
@Import({ JpaConfig.class, FollowPersistenceAdapter.class }) // ← adapter gets the shared transaction-bound EM
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class FollowPersistenceAdapterIT {

    @Autowired
    TestEntityManager tem;
    @Autowired
    FollowPersistenceAdapter adapter; // Spring-managed → same EM as repository

    // Captured after Hibernate generates IDs
    UUID followerId;
    UUID followingId;

    Follow acceptedFollow;
    Follow pendingFollow;

    @BeforeEach
    void setup() {
        // Let @UuidGenerator produce the IDs; capture them for assertions
        UserJpaEntity follower = tem.persistAndFlush(buildUser("follower"));
        UserJpaEntity following = tem.persistAndFlush(buildUser("following"));

        followerId = follower.getId();
        followingId = following.getId();

        acceptedFollow = Follow.builder()
                .followerId(followerId)
                .followingId(followingId)
                .status(FollowStatus.ACCEPTED)
                .createdAt(Instant.now())
                .build();

        pendingFollow = Follow.builder()
                .followerId(followerId)
                .followingId(followingId)
                .status(FollowStatus.PENDING)
                .createdAt(Instant.now())
                .build();
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Builds a constraint-valid {@link UserJpaEntity} without a manually set id.
     */
    private UserJpaEntity buildUser(String username) {
        return UserJpaEntity.builder()
                .username(username)
                .fullName(username)
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build();
    }

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    void save_whenSuccess_persistsFollowWithCorrectFields() {
        Follow saved = adapter.save(acceptedFollow);
        tem.flush();
        tem.clear(); // evict L1 cache → next read hits H2

        assertEquals(followerId, saved.getFollowerId());
        assertEquals(followingId, saved.getFollowingId());
        assertEquals(FollowStatus.ACCEPTED, saved.getStatus());
    }

    @Test
    void findByFollowerIdAndFollowingId_whenFound_returnsFollow() {
        adapter.save(acceptedFollow);
        tem.flush();
        tem.clear();

        Follow found = adapter.findByFollowerIdAndFollowingId(followerId, followingId).orElseThrow();

        assertEquals(followerId, found.getFollowerId());
        assertEquals(followingId, found.getFollowingId());
        assertEquals(FollowStatus.ACCEPTED, found.getStatus());
    }

    @Test
    void delete_whenFound_removesFollowRelationship() {
        adapter.save(acceptedFollow);
        tem.flush();
        tem.clear();

        adapter.delete(followerId, followingId);
        tem.flush();
        tem.clear();

        Optional<Follow> result = adapter.findByFollowerIdAndFollowingId(followerId, followingId);
        assertTrue(result.isEmpty());
    }

    @Test
    void findFollowersByUserId_whenAcceptedFollowExists_returnsFollower() {
        Pageable pageable = PageRequest.of(0, 10);
        adapter.save(acceptedFollow);
        tem.flush();
        tem.clear();

        List<Follow> followers = adapter.findFollowersByUserId(followingId, pageable);

        assertEquals(1, followers.size());
        assertEquals(FollowStatus.ACCEPTED, followers.get(0).getStatus());
    }

    @Test
    void findFollowingByUserId_whenAcceptedFollowExists_returnsFollowing() {
        Pageable pageable = PageRequest.of(0, 10);
        adapter.save(acceptedFollow);
        tem.flush();
        tem.clear();

        List<Follow> following = adapter.findFollowingByUserId(followerId, pageable);

        assertEquals(1, following.size());
        assertEquals(FollowStatus.ACCEPTED, following.get(0).getStatus());
    }

    @Test
    void findPendingRequestsByFollowingId_whenPendingFollowExists_returnsPendingRequest() {
        adapter.save(pendingFollow);
        tem.flush();
        tem.clear();

        List<Follow> pendingRequests = adapter.findPendingRequestsByFollowingId(followingId);

        assertEquals(1, pendingRequests.size());
        assertEquals(FollowStatus.PENDING, pendingRequests.get(0).getStatus());
    }

    @Test
    void countFollowersByUserId_whenAcceptedFollowExists_returnsFollowerCount() {
        adapter.save(acceptedFollow);
        tem.flush();
        tem.clear();

        long count = adapter.countFollowersByUserId(followingId);

        assertEquals(1, count);
    }

    @Test
    void countFollowingByUserId_whenAcceptedFollowExists_returnsFollowingCount() {
        adapter.save(acceptedFollow);
        tem.flush();
        tem.clear();

        long count = adapter.countFollowingByUserId(followerId);

        assertEquals(1, count);
    }

    @Test
    void findByFollowerIdAndFollowingId_whenNotFound_returnsEmpty() {
        // No follow saved — relationship does not exist
        Optional<Follow> result = adapter.findByFollowerIdAndFollowingId(followerId, followingId);

        assertTrue(result.isEmpty());
    }

    @Test
    void findFollowersByUserId_pendingFollow_isNotIncluded() {
        Pageable pageable = PageRequest.of(0, 10);
        adapter.save(pendingFollow);
        tem.flush();
        tem.clear();

        List<Follow> followers = adapter.findFollowersByUserId(followingId, pageable);

        // Pending follows must not appear in the followers list
        assertTrue(followers.isEmpty());
    }
}
