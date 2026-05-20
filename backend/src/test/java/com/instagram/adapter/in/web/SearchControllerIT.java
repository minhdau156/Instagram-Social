package com.instagram.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.instagram.domain.model.Hashtag;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.SearchHistory;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserStats;
import com.instagram.domain.port.in.post.FindAllPostMediaUseCase;
import com.instagram.domain.port.in.search.ClearSearchHistoryUseCase;
import com.instagram.domain.port.in.search.GetPostsByHashtagUseCase;
import com.instagram.domain.port.in.search.GetSearchHistoryUseCase;
import com.instagram.domain.port.in.search.SearchHashtagsUseCase;
import com.instagram.domain.port.in.search.SearchPostsUseCase;
import com.instagram.domain.port.in.search.SearchUsersUseCase;
import com.instagram.domain.port.in.user.FindAllUserUseCase;
import com.instagram.domain.port.in.user.GetUserStatsUseCase;
import com.instagram.infrastructure.security.JwtTokenProvider;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;
import com.instagram.infrastructure.security.SecurityConfig;

@WebMvcTest(SearchController.class)
@Import(SecurityConfig.class)
class SearchControllerIT {

    private static final String CURRENT_USER_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Autowired
    private MockMvc mockMvc;

    // ── Security infra mocks ─────────────────────────────────────────────── //
    @MockBean
    private JwtTokenProvider jwtTokenProvider;
    @MockBean
    private UserDetailsService userDetailsService;
    @MockBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    // ── Search use case mocks ────────────────────────────────────────────── //
    @MockBean
    private SearchUsersUseCase searchUsersUseCase;
    @MockBean
    private SearchHashtagsUseCase searchHashtagsUseCase;
    @MockBean
    private SearchPostsUseCase searchPostsUseCase;
    @MockBean
    private GetPostsByHashtagUseCase getPostsByHashtagUseCase;
    @MockBean
    private GetSearchHistoryUseCase getSearchHistoryUseCase;
    @MockBean
    private ClearSearchHistoryUseCase clearSearchHistoryUseCase;
    @MockBean
    private FindAllUserUseCase findAllUserUseCase;
    @MockBean
    private FindAllPostMediaUseCase findAllPostMediaUseCase;
    @MockBean
    private GetUserStatsUseCase getUserStatsUseCase;

    // ── Test data ────────────────────────────────────────────────────────── //

    private User buildUser() {
        return User.builder()
                .id(UUID.fromString(CURRENT_USER_ID))
                .username("testuser")
                .fullName("Test User")
                .privacyLevel(PrivacyLevel.PUBLIC)
                .build();
    }

    private Post buildPost() {
        return Post.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(CURRENT_USER_ID))
                .caption("test caption")
                .status(PostStatus.PUBLISHED)
                .likeCount(0)
                .commentCount(0)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    private Hashtag buildHashtag() {
        return Hashtag.builder()
                .id(UUID.randomUUID())
                .name("travel")
                .postCount(5)
                .build();
    }

    private SearchHistory buildSearchHistory() {
        return SearchHistory.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString(CURRENT_USER_ID))
                .query("john")
                .searchedAt(OffsetDateTime.now())
                .build();
    }

    // ── GET /search?type=users ───────────────────────────────────────────── //

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void search_byUsers_returns200WithList() throws Exception {
        User user = buildUser();
        when(searchUsersUseCase.searchUsers(any())).thenReturn(List.of(user));
        when(getUserStatsUseCase.findAllUserStatByIds(any()))
                .thenReturn(List.of(UserStats.zero(UUID.fromString(CURRENT_USER_ID))));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "john")
                        .param("type", "users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].username").value("testuser"));
    }

    // ── GET /search?type=hashtags ────────────────────────────────────────── //

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void search_byHashtags_returns200WithList() throws Exception {
        when(searchHashtagsUseCase.searchHashtags(any())).thenReturn(List.of(buildHashtag()));

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "travel")
                        .param("type", "hashtags")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("travel"));
    }

    // ── GET /search?type=posts ───────────────────────────────────────────── //

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void search_byPosts_returns200WithList() throws Exception {
        Post post = buildPost();
        User user = buildUser();
        when(searchPostsUseCase.searchPosts(any())).thenReturn(List.of(post));
        when(findAllUserUseCase.findAllByIds(any())).thenReturn(List.of(user));
        when(findAllPostMediaUseCase.findAllByPostIds(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "sunset")
                        .param("type", "posts")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].caption").value("test caption"));
    }

    // ── GET /search?type=invalid ─────────────────────────────────────────── //

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void search_unknownType_returns400BadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "x")
                        .param("type", "invalid_type")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    // ── GET /search?q=&type=users (blank q) ─────────────────────────────── //

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void search_blankQuery_returns200WithEmptyList() throws Exception {
        when(searchUsersUseCase.searchUsers(any())).thenReturn(List.of());
        when(getUserStatsUseCase.findAllUserStatByIds(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/search")
                        .param("q", "")
                        .param("type", "users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ── GET /search/history ──────────────────────────────────────────────── //

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void getSearchHistory_authenticated_returns200WithList() throws Exception {
        when(getSearchHistoryUseCase.getSearchHistory(any())).thenReturn(List.of(buildSearchHistory()));

        mockMvc.perform(get("/api/v1/search/history")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].query").value("john"));
    }

    // ── DELETE /search/history ───────────────────────────────────────────── //

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void clearSearchHistory_authenticated_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/search/history")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    // ── GET /hashtags/{name}/posts ───────────────────────────────────────── //

    @Test
    @WithMockUser(username = CURRENT_USER_ID)
    void getPostsByHashtag_authenticated_returns200WithList() throws Exception {
        Post post = buildPost();
        User user = buildUser();
        when(getPostsByHashtagUseCase.getPostsByHashtag(any())).thenReturn(List.of(post));
        when(findAllUserUseCase.findAllByIds(any())).thenReturn(List.of(user));
        when(findAllPostMediaUseCase.findAllByPostIds(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/hashtags/{name}/posts", "travel")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].caption").value("test caption"));
    }

    // ── Unauthenticated → 401 ────────────────────────────────────────────── //

    @Test
    void search_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/search")
                        .param("q", "john")
                        .param("type", "users")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized());
    }
}
