package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.instagram.adapter.out.persistence.entity.FollowId;
import com.instagram.adapter.out.persistence.entity.FollowJpaEntity;
import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.repository.FeedJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.UserStatus;
import com.instagram.infrastructure.config.JpaConfig;
import com.instagram.infrastructure.util.BlockFilter;

@DataJpaTest
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class FeedJpaQueryAdapterIT {

    @Autowired
    private TestEntityManager tem;

    @Autowired
    private FeedJpaRepository feedJpaRepository;

    @Autowired
    private PostJpaRepository postJpaRepository;

    private FeedJpaQueryAdapter adapter;

    private UserJpaEntity follower;
    private UserJpaEntity followed;
    private UserJpaEntity stranger;

    @BeforeEach
    void setUp() {

        BlockFilter noBlockFilter = mock(BlockFilter.class);

        when(noBlockFilter.getExcludedUserIds(any())).thenReturn(Collections.emptySet());
        adapter = new FeedJpaQueryAdapter(feedJpaRepository, postJpaRepository, noBlockFilter, tem.getEntityManager());

        follower = tem.persistAndFlush(buildUser("follower"));
        followed = tem.persistAndFlush(buildUser("followed"));
        stranger = tem.persistAndFlush(buildUser("stranger"));

        // follower -> followed (approved)
        tem.persistAndFlush(FollowJpaEntity.builder()
                .id(new FollowId(follower.getId(), followed.getId()))
                .follower(follower)
                .following(followed)
                .isApproved(true)
                .build());

        // Posts: 2 from followed, 1 from stranger
        tem.persistAndFlush(buildPost(followed));
        tem.persistAndFlush(buildPost(followed));
        tem.persistAndFlush(buildPost(stranger));

        tem.flush();
        tem.clear();
    }

    @Test
    void findHomeFeed_returnsOnlyFollowedUsersPosts() {
        List<PostJpaEntity> feed = feedJpaRepository.findHomeFeed(follower.getId(), null, 20);

        assertThat(feed).hasSize(2);
        assertThat(feed).allMatch(p -> p.getUserId().equals(followed.getId()));
    }

    @Test
    void findExploreFeed_excludesFollowedUsers() {
        List<PostJpaEntity> explore = feedJpaRepository.findExploreFeed(follower.getId(), null, 20);

        assertThat(explore).hasSize(1);
        assertThat(explore.get(0).getUserId()).isEqualTo(stranger.getId());
    }

    @Test
    void findHomeFeed_cursorPagination_returnsEmptyWhenCursorIsMinUuid() {
        // UUID.fromString("00000000-...") is the minimum possible UUID;
        // no post id can be less than it, so the page must be empty.
        UUID minUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");

        List<PostJpaEntity> page = feedJpaRepository.findHomeFeed(follower.getId(), minUuid, 20);

        assertThat(page).isEmpty();
    }

    @Test
    void getHomeFeed_noN1_statementCountBelowThreshold() {
        // Seed 5 additional posts (7 total from followed). With the old N+1
        // (entity.getUser().getId()), this would fire 1 list query + 7 lazy-load
        // queries = 8 statements. With the fix (entity.getUserId()), only 1.
        for (int i = 0; i < 5; i++) {
            tem.persistAndFlush(buildPost(followed));
        }
        tem.flush();
        tem.clear();

        Statistics stats = tem.getEntityManager()
                .getEntityManagerFactory()
                .unwrap(SessionFactory.class)
                .getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        adapter.getHomeFeed(follower.getId(), null, 20);

        assertThat(stats.getPrepareStatementCount()).isLessThan(5);
    }

    private UserJpaEntity buildUser(String username) {
        return UserJpaEntity.builder()
                .username(username)
                .fullName(username)
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build();
    }

    private PostJpaEntity buildPost(UserJpaEntity user) {
        return PostJpaEntity.builder()
                .userId(user.getId())
                .status(PostStatus.PUBLISHED)
                .build();
    }
}
