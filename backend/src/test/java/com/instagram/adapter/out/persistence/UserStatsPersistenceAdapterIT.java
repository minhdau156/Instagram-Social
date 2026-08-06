package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.repository.UserJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserStatsJpaRepository;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.UserStats;
import com.instagram.domain.model.UserStatus;

public class UserStatsPersistenceAdapterIT extends PostgresIntegrationTest {

    @Autowired
    private UserStatsJpaRepository userStatsJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    private UserStatsPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new UserStatsPersistenceAdapter(userStatsJpaRepository);
    }

    // user_stats.user_id is a real FK to users.id — every row we create needs an
    // actually-persisted parent user rather than a bare UUID.randomUUID().
    private UUID persistUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userJpaRepository.save(UserJpaEntity.builder()
                .username("stats_" + suffix)
                .email("stats_" + suffix + "@example.com")
                .fullName("Stats Test User")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build()).getId();
    }

    @Test
    void findByUserId_whenExists_returnsStats() {
        // given
        UUID userId = persistUser();
        adapter.create(new UserStats(userId, 2, 10, 5));

        // when
        Optional<UserStats> result = adapter.findByUserId(userId);

        // then
        assertTrue(result.isPresent());
        assertEquals(userId, result.get().userId());
        assertEquals(2, result.get().postCount());
        assertEquals(10, result.get().followerCount());
        assertEquals(5, result.get().followingCount());
    }

    @Test
    void findByUserId_whenNotExists_returnsEmpty() {
        // given / when
        Optional<UserStats> result = adapter.findByUserId(UUID.randomUUID());

        // then
        assertTrue(result.isEmpty());
    }

    @Test
    void create_persistsStatsWithCorrectFields() {
        // given
        UUID userId = persistUser();

        // when
        adapter.create(new UserStats(userId, 3, 20, 15));

        // then
        Optional<UserStats> saved = adapter.findByUserId(userId);
        assertTrue(saved.isPresent());
        assertEquals(3, saved.get().postCount());
        assertEquals(20, saved.get().followerCount());
        assertEquals(15, saved.get().followingCount());
    }

    @Test
    void incrementFollowerCount_whenStatsExist_incrementsCount() {
        // given
        UUID userId = persistUser();
        adapter.create(new UserStats(userId, 0, 5, 0));

        // when
        adapter.incrementFollowerCount(userId);

        // then
        UserStats result = adapter.findByUserId(userId).orElseThrow();
        assertEquals(6, result.followerCount());
    }

    @Test
    void incrementFollowerCount_whenStatsNotExist_createsAndIncrements() {
        // given
        UUID userId = persistUser();

        // when
        adapter.incrementFollowerCount(userId);

        // then
        UserStats result = adapter.findByUserId(userId).orElseThrow();
        assertEquals(1, result.followerCount());
    }

    @Test
    void decrementFollowerCount_whenStatsExist_decrementsCount() {
        // given
        UUID userId = persistUser();
        adapter.create(new UserStats(userId, 0, 3, 0));

        // when
        adapter.decrementFollowerCount(userId);

        // then
        UserStats result = adapter.findByUserId(userId).orElseThrow();
        assertEquals(2, result.followerCount());
    }

    @Test
    void incrementFollowingCount_whenStatsExist_incrementsCount() {
        // given
        UUID userId = persistUser();
        adapter.create(new UserStats(userId, 0, 0, 2));

        // when
        adapter.incrementFollowingCount(userId);

        // then
        UserStats result = adapter.findByUserId(userId).orElseThrow();
        assertEquals(3, result.followingCount());
    }

    @Test
    void incrementFollowingCount_whenStatsNotExist_createsAndIncrements() {
        // given
        UUID userId = persistUser();

        // when
        adapter.incrementFollowingCount(userId);

        // then
        UserStats result = adapter.findByUserId(userId).orElseThrow();
        assertEquals(1, result.followingCount());
    }

    @Test
    void decrementFollowingCount_whenStatsExist_decrementsCount() {
        // given
        UUID userId = persistUser();
        adapter.create(new UserStats(userId, 0, 0, 4));

        // when
        adapter.decrementFollowingCount(userId);

        // then
        UserStats result = adapter.findByUserId(userId).orElseThrow();
        assertEquals(3, result.followingCount());
    }

    @Test
    void findAllByIds_returnsOnlyMatchingStats() {
        // given
        UUID userId1 = persistUser();
        UUID userId2 = persistUser();
        UUID userId3 = persistUser();
        adapter.create(new UserStats(userId1, 1, 10, 5));
        adapter.create(new UserStats(userId2, 2, 20, 8));
        adapter.create(new UserStats(userId3, 3, 30, 12));

        // when
        List<UserStats> result = adapter.findAllByIds(List.of(userId1, userId2));

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(UserStats::userId).containsExactlyInAnyOrder(userId1, userId2);
    }

    @Test
    void findAllByIds_whenNoMatch_returnsEmpty() {
        // given / when
        List<UserStats> result = adapter.findAllByIds(List.of(UUID.randomUUID()));

        // then
        assertThat(result).isEmpty();
    }
}
