package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

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
import com.instagram.infrastructure.config.JpaConfig;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.datasource.url=jdbc:h2:mem:searchtest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password="
})
@Sql(statements = {
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS follower_count INT NOT NULL DEFAULT 0",
        "ALTER TABLE users ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMP",
        "CREATE TABLE IF NOT EXISTS post_hashtags (post_id UUID NOT NULL, hashtag_id UUID NOT NULL, PRIMARY KEY (post_id, hashtag_id))"
}, executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SearchJpaAdapterIT {

    @Autowired
    private TestEntityManager tem;

    @Autowired
    private SearchHistoryJpaRepository searchHistoryJpaRepository;

    private SearchJpaAdapter adapter;
    private SearchHistoryPersistenceAdapter historyAdapter;

    @BeforeEach
    void setUp() {
        adapter = new SearchJpaAdapter(tem.getEntityManager());
        historyAdapter = new SearchHistoryPersistenceAdapter(searchHistoryJpaRepository);
    }

    // ── searchUsers ──────────────────────────────────────────────────────── //

    @Test
    void searchUsers_matchesOnUsername() {
        tem.persistAndFlush(buildUser("john_doe", "Alice Smith"));
        tem.persistAndFlush(buildUser("alice99", "Alice Roberts"));
        tem.clear();

        List<User> results = adapter.searchUsers("john", Pageable.ofSize(20));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUsername()).isEqualTo("john_doe");
    }

    @Test
    void searchUsers_matchesOnFullName() {
        tem.persistAndFlush(buildUser("jsmith", "John Smith"));
        tem.persistAndFlush(buildUser("bjones", "Bob Jones"));
        tem.clear();

        List<User> results = adapter.searchUsers("smith", Pageable.ofSize(20));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getUsername()).isEqualTo("jsmith");
    }

    @Test
    void searchUsers_isCaseInsensitiveViaIlike() {
        tem.persistAndFlush(buildUser("testuser_ci", "Test User CI"));
        tem.clear();

        List<User> results = adapter.searchUsers("TESTUSER_CI", Pageable.ofSize(20));

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

        List<User> results = adapter.searchUsers("deleted_john", Pageable.ofSize(20));

        assertThat(results).isEmpty();
    }

    @Test
    void searchUsers_noMatch_returnsEmptyList() {
        tem.persistAndFlush(buildUser("alice_nomatch", "Alice Nobody"));
        tem.clear();

        List<User> results = adapter.searchUsers("zzz_no_match_xyz", Pageable.ofSize(20));

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
                .user(user).caption("Beautiful Sunset view").status(PostStatus.PUBLISHED).build());
        tem.persistAndFlush(PostJpaEntity.builder()
                .user(user).caption("Mountain hike").status(PostStatus.PUBLISHED).build());
        tem.clear();

        List<Post> results = adapter.searchPosts("SUNSET", Pageable.ofSize(20));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCaption()).isEqualTo("Beautiful Sunset view");
    }

    @Test
    void searchPosts_excludesSoftDeletedPosts() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("poster2", "Post User Two"));
        tem.persistAndFlush(PostJpaEntity.builder()
                .user(user).caption("Sunset deleted post").status(PostStatus.PUBLISHED)
                .deletedAt(OffsetDateTime.now()).build());
        tem.clear();

        List<Post> results = adapter.searchPosts("sunset", Pageable.ofSize(20));

        assertThat(results).isEmpty();
    }

    @Test
    void searchPosts_noMatch_returnsEmptyList() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("poster3", "Post User Three"));
        tem.persistAndFlush(PostJpaEntity.builder()
                .user(user).caption("Completely unrelated content").status(PostStatus.PUBLISHED).build());
        tem.clear();

        List<Post> results = adapter.searchPosts("zzz_no_match", Pageable.ofSize(20));

        assertThat(results).isEmpty();
    }

    // ── findPostsByHashtag ───────────────────────────────────────────────── //

    @Test
    void findPostsByHashtag_returnsOnlyLinkedPosts() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("poster4", "Post User Four"));
        PostJpaEntity linkedPost = tem.persistAndFlush(PostJpaEntity.builder()
                .user(user).caption("My travel photo").status(PostStatus.PUBLISHED).build());
        tem.persistAndFlush(PostJpaEntity.builder()
                .user(user).caption("Not tagged").status(PostStatus.PUBLISHED).build());
        HashtagJpaEntity hashtag = tem.persistAndFlush(buildHashtag("travel_d", 1));

        tem.getEntityManager()
                .createNativeQuery("INSERT INTO post_hashtags (post_id, hashtag_id) VALUES (:p, :h)")
                .setParameter("p", linkedPost.getId())
                .setParameter("h", hashtag.getId())
                .executeUpdate();
        tem.flush();
        tem.clear();

        List<Post> results = adapter.findPostsByHashtag("travel_d", Pageable.ofSize(20));

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getId()).isEqualTo(linkedPost.getId());
    }

    @Test
    void findPostsByHashtag_excludesSoftDeletedPosts() {
        UserJpaEntity user = tem.persistAndFlush(buildUser("poster5", "Post User Five"));
        PostJpaEntity deletedPost = tem.persistAndFlush(PostJpaEntity.builder()
                .user(user).caption("Travel deleted").status(PostStatus.PUBLISHED)
                .deletedAt(OffsetDateTime.now()).build());
        HashtagJpaEntity hashtag = tem.persistAndFlush(buildHashtag("travel_e", 1));

        tem.getEntityManager()
                .createNativeQuery("INSERT INTO post_hashtags (post_id, hashtag_id) VALUES (:p, :h)")
                .setParameter("p", deletedPost.getId())
                .setParameter("h", hashtag.getId())
                .executeUpdate();
        tem.flush();
        tem.clear();

        List<Post> results = adapter.findPostsByHashtag("travel_e", Pageable.ofSize(20));

        assertThat(results).isEmpty();
    }

    // ── SearchHistoryPersistenceAdapter ──────────────────────────────────── //

    @Test
    void searchHistory_save_roundTrip() {
        UUID userId = UUID.randomUUID();
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
        UUID userId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();

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
