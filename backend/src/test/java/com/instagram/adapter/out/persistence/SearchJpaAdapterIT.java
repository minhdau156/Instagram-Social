package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Pageable;

import com.instagram.adapter.out.persistence.entity.HashtagJpaEntity;
import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.repository.SearchHistoryJpaRepository;
import com.instagram.domain.model.Hashtag;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.model.PrivacyLevel;

import com.instagram.domain.model.SearchHistory;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserStatus;
import com.instagram.infrastructure.util.BlockFilter;

class SearchJpaAdapterIT extends PostgresIntegrationTest {

    @Autowired
    private TestEntityManager tem;

    @Autowired
    private SearchHistoryJpaRepository searchHistoryJpaRepository;

    private SearchJpaAdapter adapter;
    private SearchHistoryPersistenceAdapter historyAdapter;

    @BeforeEach
    void setUp() {
        // Clear all tables to ensure test isolation on ephemeral Testcontainers
        // PostgreSQL
        tem.getEntityManager()
                .createNativeQuery("TRUNCATE TABLE search_history, post_hashtags, posts, hashtags, users CASCADE")
                .executeUpdate();
        tem.flush();
        tem.clear();

        BlockFilter noBlockFilter = mock(BlockFilter.class);
        when(noBlockFilter.getExcludedUserIds(any())).thenReturn(Collections.emptySet());
        adapter = new SearchJpaAdapter(tem.getEntityManager(), noBlockFilter);
        historyAdapter = new SearchHistoryPersistenceAdapter(searchHistoryJpaRepository);
    }

    // ── searchUsers ──────────────────────────────────────────────────────── //

    @Test
    void searchUsers_matchesOnUsername() {
        tem.persistAndFlush(buildUser("john_doe", "Alice Smith"));
        tem.persistAndFlush(buildUser("alice99", "Alice Roberts"));
        tem.clear();

        List<User> results = adapter.searchUsers(UUID.randomUUID(), "john", Pageable.ofSize(20));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUsername()).isEqualTo("john_doe");
    }

    @Test
    void searchUsers_matchesOnFullName() {
        tem.persistAndFlush(buildUser("jsmith", "John Smith"));
        tem.persistAndFlush(buildUser("bjones", "Bob Jones"));
        tem.clear();

        List<User> results = adapter.searchUsers(UUID.randomUUID(),
                "smith", Pageable.ofSize(20));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUsername()).isEqualTo("jsmith");
    }

    @Test
    void searchUsers_isCaseInsensitiveViaIlike() {
        tem.persistAndFlush(buildUser("testuser_ci", "Test User CI"));
        tem.clear();

        List<User> results = adapter.searchUsers(UUID.randomUUID(), "TESTUSER_CI", Pageable.ofSize(20));

        assertThat(results).hasSize(1);
    }

    @Test
    void searchUsers_excludesSoftDeletedUsers() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("deleted_john", "John Deleted"));
        tem.getEntityManager()
                .createNativeQuery("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE id = :id")
                .setParameter("id", user.getId())
                .executeUpdate();
        tem.clear();

        List<User> results = adapter.searchUsers(UUID.randomUUID(), "deleted_john", Pageable.ofSize(20));

        assertThat(results).isEmpty();
    }

    @Test
    void searchUsers_noMatch_returnsEmptyList() {
        tem.persistAndFlush(buildUser("alice_nomatch", "Alice Nobody"));
        tem.clear();

        List<User> results = adapter.searchUsers(UUID.randomUUID(), "zzz_no_match_xyz", Pageable.ofSize(20));

        assertThat(results).isEmpty();
    }

    // ── searchHashtags ───────────────────────────────────────────────────── //

    @Test
    void searchHashtags_prefixMatch_returnsBothTravelAndTravelblog() {
        tem.persistAndFlush(buildHashtag("travel_a", 10));
        tem.persistAndFlush(buildHashtag("travel_ablog", 5));
        tem.persistAndFlush(buildHashtag("food_a", 20));
        tem.clear();

        List<Hashtag> results = adapter.searchHashtags("travel_a", Pageable.ofSize(20));

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Hashtag::getName).containsExactlyInAnyOrder("travel_a", "travel_ablog");
    }

    @Test
    void searchHashtags_orderedByPostCountDesc() {
        tem.persistAndFlush(buildHashtag("hiking_b", 3));
        tem.persistAndFlush(buildHashtag("hiking_btrails", 15));
        tem.clear();

        List<Hashtag> results = adapter.searchHashtags("hiking_b", Pageable.ofSize(20));

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getName()).isEqualTo("hiking_btrails");
        assertThat(results.get(1).getName()).isEqualTo("hiking_b");
    }

    @Test
    void searchHashtags_excludesNonPrefixMatch() {
        tem.persistAndFlush(buildHashtag("food_c", 10));
        tem.clear();

        // prefix search for "travel" should not match "food"
        List<Hashtag> results = adapter.searchHashtags("travel_c", Pageable.ofSize(20));

        assertThat(results).isEmpty();
    }

    // ── searchPosts ──────────────────────────────────────────────────────── //

    @Test
    void searchPosts_matchesCaptionIlikeCaseInsensitive() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("poster1", "Post User One"));
        tem.persistAndFlush(PostJpaEntity.builder()
                .userId(user.getId()).caption("Beautiful Sunset view").status(PostStatus.PUBLISHED).build());
        tem.persistAndFlush(PostJpaEntity.builder()
                .userId(user.getId()).caption("Mountain hike").status(PostStatus.PUBLISHED).build());
        tem.clear();

        List<Post> results = adapter.searchPosts(UUID.randomUUID(), "SUNSET", Pageable.ofSize(20));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCaption()).isEqualTo("Beautiful Sunset view");
    }

    @Test
    void searchPosts_excludesSoftDeletedPosts() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("poster2", "Post User Two"));
        tem.persistAndFlush(PostJpaEntity.builder()
                .userId(user.getId()).caption("Sunset deleted post").status(PostStatus.PUBLISHED)
                .deletedAt(OffsetDateTime.now()).build());
        tem.clear();

        List<Post> results = adapter.searchPosts(UUID.randomUUID(), "sunset", Pageable.ofSize(20));

        assertThat(results).isEmpty();
    }

    @Test
    void searchPosts_noMatch_returnsEmptyList() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("poster3", "Post User Three"));
        tem.persistAndFlush(PostJpaEntity.builder()
                .userId(user.getId()).caption("Completely unrelated content").status(PostStatus.PUBLISHED).build());
        tem.clear();

        List<Post> results = adapter.searchPosts(UUID.randomUUID(), "zzz_no_match", Pageable.ofSize(20));

        assertThat(results).isEmpty();
    }

    // ── findPostsByHashtag ───────────────────────────────────────────────── //

    @Test
    void findPostsByHashtag_returnsOnlyLinkedPosts() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("poster4", "Post User Four"));
        PostJpaEntity linkedPost = tem.persistAndFlush(PostJpaEntity.builder()
                .userId(user.getId()).caption("My travel photo").status(PostStatus.PUBLISHED).build());
        tem.persistAndFlush(PostJpaEntity.builder()
                .userId(user.getId()).caption("Not tagged").status(PostStatus.PUBLISHED).build());
        HashtagJpaEntity hashtag = tem.persistAndFlush(buildHashtag("travel_d", 1));

        tem.getEntityManager()
                .createNativeQuery("INSERT INTO post_hashtags (post_id, hashtag_id) VALUES (:p, :h)")
                .setParameter("p", linkedPost.getId())
                .setParameter("h", hashtag.getId())
                .executeUpdate();
        tem.flush();
        tem.clear();

        List<Post> results = adapter.findPostsByHashtag(UUID.randomUUID(), "travel_d", Pageable.ofSize(20));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(linkedPost.getId());
    }

    @Test
    void findPostsByHashtag_excludesSoftDeletedPosts() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("poster5", "Post User Five"));
        PostJpaEntity deletedPost = tem.persistAndFlush(PostJpaEntity.builder()
                .userId(user.getId()).caption("Travel deleted").status(PostStatus.PUBLISHED)
                .deletedAt(OffsetDateTime.now()).build());
        HashtagJpaEntity hashtag = tem.persistAndFlush(buildHashtag("travel_e", 1));

        tem.getEntityManager()
                .createNativeQuery("INSERT INTO post_hashtags (post_id, hashtag_id) VALUES (:p, :h)")
                .setParameter("p", deletedPost.getId())
                .setParameter("h", hashtag.getId())
                .executeUpdate();
        tem.flush();
        tem.clear();

        List<Post> results = adapter.findPostsByHashtag(UUID.randomUUID(), "travel_e", Pageable.ofSize(20));

        assertThat(results).isEmpty();
    }

    // ── SearchHistoryPersistenceAdapter ──────────────────────────────────── //

    @Test
    void searchHistory_save_roundTrip() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("history_user1", "History User One"));
        UUID userId = user.getId();
        SearchHistory history = SearchHistory.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .query("test query")
                .searchedAt(OffsetDateTime.now())
                .build();

        SearchHistory saved = historyAdapter.save(history);
        List<SearchHistory> found = historyAdapter.findByUserIdOrderBySearchedAtDesc(userId, Pageable.ofSize(10));

        assertThat(saved.getId()).isNotNull();
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getUserId()).isEqualTo(userId);
        assertThat(found.get(0).getQuery()).isEqualTo("test query");
    }

    @Test
    void searchHistory_deleteByUserId_removesAllForUserAndLeavesOthers() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("history_user2", "History User Two"));
        UserJpaEntity otherUser = tem.persistAndFlush(buildUser("history_user3", "History User Three"));
        UUID userId = user.getId();
        UUID otherUserId = otherUser.getId();

        historyAdapter.save(SearchHistory.builder()
                .id(UUID.randomUUID()).userId(userId).query("q1").searchedAt(OffsetDateTime.now()).build());
        historyAdapter.save(SearchHistory.builder()
                .id(UUID.randomUUID()).userId(userId).query("q2").searchedAt(OffsetDateTime.now()).build());
        historyAdapter.save(SearchHistory.builder()
                .id(UUID.randomUUID()).userId(otherUserId).query("q3").searchedAt(OffsetDateTime.now()).build());

        historyAdapter.deleteByUserId(userId);

        assertThat(historyAdapter.findByUserIdOrderBySearchedAtDesc(userId, Pageable.ofSize(10))).isEmpty();
        assertThat(historyAdapter.findByUserIdOrderBySearchedAtDesc(otherUserId, Pageable.ofSize(10))).hasSize(1);
    }

    // ── FTS Specific Cases ────────────────────────────────────────────────── //

    @Test
    void searchPosts_ftsStemming() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("stem_poster", "Stemming User"));
        tem.persistAndFlush(PostJpaEntity.builder()
                .userId(user.getId()).caption("Running along the beautiful beach").status(PostStatus.PUBLISHED)
                .build());
        tem.clear();

        // "running" stems to "run" in English FTS. Searching "run" should match.
        List<Post> results = adapter.searchPosts(UUID.randomUUID(), "run", Pageable.ofSize(20));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCaption()).isEqualTo("Running along the beautiful beach");
    }

    @Test
    void searchPosts_ftsMultiWord() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("bridge_poster", "Bridge User"));
        tem.persistAndFlush(PostJpaEntity.builder()
                .userId(user.getId()).caption("Beautiful walk on the Golden Gate Bridge").status(PostStatus.PUBLISHED)
                .build());
        tem.clear();

        List<Post> results = adapter.searchPosts(UUID.randomUUID(), "golden gate", Pageable.ofSize(20));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCaption()).isEqualTo("Beautiful walk on the Golden Gate Bridge");
    }

    @Test
    void searchPosts_ftsRelevanceOrdering() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("rank_poster", "Ranking User"));
        PostJpaEntity postLowDensity = tem.persistAndFlush(PostJpaEntity.builder()
                .userId(user.getId()).caption("A beautiful sunset is happening in Bali right now")
                .status(PostStatus.PUBLISHED)
                .build());
        PostJpaEntity postHighDensity = tem.persistAndFlush(PostJpaEntity.builder()
                .userId(user.getId()).caption("sunset sunset sunset is sunset beach").status(PostStatus.PUBLISHED)
                .build());
        tem.clear();

        List<Post> results = adapter.searchPosts(UUID.randomUUID(), "sunset", Pageable.ofSize(20));

        assertThat(results).hasSize(2);
        // The post with higher density of the keyword "sunset" should rank first due to
        // ts_rank
        assertThat(results.get(0).getId()).isEqualTo(postHighDensity.getId());
        assertThat(results.get(1).getId()).isEqualTo(postLowDensity.getId());
    }

    @Test
    void searchPosts_shortQueryFallback() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("fallback_poster", "Fallback User"));
        tem.persistAndFlush(PostJpaEntity.builder()
                .userId(user.getId()).caption("Beautiful sunset in Bali").status(PostStatus.PUBLISHED).build());
        tem.clear();

        // Query "ba" is length 2 (< 3 min length). Under FTS it won't match "Bali",
        // but the short-query fallback should use ILIKE '%ba%' and find it.
        List<Post> results = adapter.searchPosts(UUID.randomUUID(), "ba", Pageable.ofSize(20));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCaption()).isEqualTo("Beautiful sunset in Bali");
    }

    @Test
    void searchUsers_shortQueryFallback() {
        tem.persistAndFlush(buildUser("john_doe", "John Doe"));
        tem.clear();

        // Query "jo" is length 2 (< 3 min length). FTS won't match it,
        // but short-query ILIKE fallback should match on username "john_doe".
        List<User> results = adapter.searchUsers(UUID.randomUUID(), "jo", Pageable.ofSize(20));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUsername()).isEqualTo("john_doe");
    }

    @Test
    void searchUsers_ftsNoCrossTokenPartialMatch() {
        tem.persistAndFlush(buildUser("alex_j", "Alexander Johnson"));
        tem.clear();

        // Query "alexand" is length 7 (>= 3 -> FTS). Simple dictionary splits into full
        // tokens:
        // "alexander" and "johnson". Since "alexand" is not a complete token, FTS will
        // NOT match it.
        // This is expected FTS behavior.
        List<User> results = adapter.searchUsers(UUID.randomUUID(), "alexand", Pageable.ofSize(20));

        assertThat(results).isEmpty();
    }

    @Test
    void searchUsers_ftsFollowerCountOrdering() {
        UserJpaEntity userA = tem.persistAndFlush(buildUser("travel_a", "Traveler Alice"));
        UserJpaEntity userB = tem.persistAndFlush(buildUser("travel_b", "Traveler Bob"));

        // Manually update follower_count in DB since UserJpaEntity doesn't expose it
        tem.getEntityManager()
                .createNativeQuery("UPDATE users SET follower_count = :count WHERE id = :id")
                .setParameter("count", 10)
                .setParameter("id", userA.getId())
                .executeUpdate();

        tem.getEntityManager()
                .createNativeQuery("UPDATE users SET follower_count = :count WHERE id = :id")
                .setParameter("count", 100)
                .setParameter("id", userB.getId())
                .executeUpdate();

        tem.flush();
        tem.clear();

        List<User> results = adapter.searchUsers(UUID.randomUUID(), "Traveler", Pageable.ofSize(20));

        assertThat(results).hasSize(2);
        // bob (100 followers) should be first, alice (10 followers) should be second
        assertThat(results.get(0).getUsername()).isEqualTo("travel_b");
        assertThat(results.get(1).getUsername()).isEqualTo("travel_a");
    }

    // ── Helpers ──────────────────────────────────────────────────────────── //

    private UserJpaEntity buildUser(String username, String fullName) {
        return UserJpaEntity.builder()
                .username(username)
                .email(username + "@example.com")
                .fullName(fullName)
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build();
    }

    private HashtagJpaEntity buildHashtag(String name, int postCount) {
        return HashtagJpaEntity.builder()
                .name(name)
                .postCount(postCount)
                .build();
    }
}
