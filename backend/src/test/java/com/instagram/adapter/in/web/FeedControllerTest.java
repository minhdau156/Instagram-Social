package com.instagram.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;

import com.instagram.domain.model.Hashtag;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.port.in.feed.GetExploreFeedUseCase;
import com.instagram.domain.port.in.feed.GetHomeFeedUseCase;
import com.instagram.domain.port.out.FeedRepository;
import com.instagram.infrastructure.security.JwtTokenProvider;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;
import com.instagram.infrastructure.security.SecurityConfig;

@WebMvcTest(FeedController.class)
@Import(SecurityConfig.class)
class FeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @MockBean
    private GetHomeFeedUseCase getHomeFeedUseCase;

    @MockBean
    private GetExploreFeedUseCase getExploreFeedUseCase;

    @MockBean
    private FeedRepository feedRepository;

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void getHomeFeed_returns200WithPostsAndNextCursor() throws Exception {
        UUID nextCursor = UUID.randomUUID();
        Post post = Post.builder()
                .id(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .status(PostStatus.PUBLISHED)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        when(getHomeFeedUseCase.getHomeFeed(any()))
                .thenReturn(new GetHomeFeedUseCase.FeedPage(List.of(post), nextCursor));

        mockMvc.perform(get("/api/v1/feed").param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.posts").isArray())
                .andExpect(jsonPath("$.data.posts[0]").exists())
                .andExpect(jsonPath("$.data.nextCursor").value(nextCursor.toString()));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void getHomeFeed_cursorParam_passedToUseCase() throws Exception {
        UUID cursor = UUID.randomUUID();
        when(getHomeFeedUseCase.getHomeFeed(any()))
                .thenReturn(new GetHomeFeedUseCase.FeedPage(List.of(), null));

        mockMvc.perform(get("/api/v1/feed").param("cursor", cursor.toString()))
                .andExpect(status().isOk());

        verify(getHomeFeedUseCase).getHomeFeed(
                argThat(q -> cursor.equals(q.cursor())));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void getHomeFeed_invalidCursorParam_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/feed").param("cursor", "not-a-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void getTrendingHashtags_returns200WithHashtagList() throws Exception {
        Hashtag hashtag = Hashtag.builder()
                .id(UUID.randomUUID())
                .name("spring")
                .postCount(42)
                .build();
        when(feedRepository.getTrendingHashtags(10)).thenReturn(List.of(hashtag));

        mockMvc.perform(get("/api/v1/explore/hashtags"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("spring"))
                .andExpect(jsonPath("$.data[0].postCount").value(42));
    }
}
