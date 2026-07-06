package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.instagram.adapter.out.persistence.entity.UserStatsJpaEntity;
import com.instagram.infrastructure.config.JpaConfig;

@DataJpaTest
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserStatsJpaEntityIT {

    @Autowired
    private TestEntityManager entityManager;

    private UserStatsJpaEntity buildStats(UUID userId, long postCount, long followerCount, long followingCount) {
        return UserStatsJpaEntity.builder()
                .userId(userId)
                .postCount(postCount)
                .followerCount(followerCount)
                .followingCount(followingCount)
                .build();
    }

    @Test
    void shouldPersistAndRetrieveWithAllCounters() {
        UUID userId = UUID.randomUUID();
        UserStatsJpaEntity saved = entityManager.persistFlushFind(buildStats(userId, 5, 100, 50));

        assertThat(saved.getUserId()).isEqualTo(userId);
        assertThat(saved.getPostCount()).isEqualTo(5);
        assertThat(saved.getFollowerCount()).isEqualTo(100);
        assertThat(saved.getFollowingCount()).isEqualTo(50);
    }

    @Test
    void shouldPersistZeroCounters() {
        UUID userId = UUID.randomUUID();
        UserStatsJpaEntity saved = entityManager.persistFlushFind(buildStats(userId, 0, 0, 0));

        assertThat(saved.getPostCount()).isZero();
        assertThat(saved.getFollowerCount()).isZero();
        assertThat(saved.getFollowingCount()).isZero();
    }

    @Test
    void shouldUseUserIdAsPrimaryKey() {
        UUID userId = UUID.randomUUID();
        entityManager.persistAndFlush(buildStats(userId, 1, 2, 3));
        entityManager.clear();

        UserStatsJpaEntity found = entityManager.find(UserStatsJpaEntity.class, userId);

        assertThat(found).isNotNull();
        assertThat(found.getUserId()).isEqualTo(userId);
    }

    @Test
    void shouldStoreEachUsersSeparately() {
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        entityManager.persistAndFlush(buildStats(userId1, 10, 200, 50));
        entityManager.persistAndFlush(buildStats(userId2, 3, 40, 80));
        entityManager.clear();

        UserStatsJpaEntity stats1 = entityManager.find(UserStatsJpaEntity.class, userId1);
        UserStatsJpaEntity stats2 = entityManager.find(UserStatsJpaEntity.class, userId2);

        assertThat(stats1.getPostCount()).isEqualTo(10);
        assertThat(stats2.getPostCount()).isEqualTo(3);
        assertThat(stats1.getFollowerCount()).isNotEqualTo(stats2.getFollowerCount());
    }
}
