package com.instagram.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.instagram.domain.exception.AlreadySavedException;
import com.instagram.domain.model.SavedPost;
import com.instagram.domain.port.in.save.GetSavedPostsUseCase;
import com.instagram.domain.port.in.save.SavePostUseCase;
import com.instagram.domain.port.in.save.UnsavePostUseCase;
import com.instagram.infrastructure.security.JwtTokenProvider;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;
import com.instagram.infrastructure.security.SecurityConfig;

@WebMvcTest(SaveController.class)
@Import(SecurityConfig.class)
class SaveControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private JwtTokenProvider jwtTokenProvider;

        @MockBean
        private UserDetailsService userDetailsService;

        @MockBean
        private OAuth2SuccessHandler oAuth2SuccessHandler;

        @MockBean
        private SavePostUseCase savePostUseCase;

        @MockBean
        private UnsavePostUseCase unsavePostUseCase;

        @MockBean
        private GetSavedPostsUseCase getSavedPostsUseCase;

        private static final String MOCK_USER_ID = "123e4567-e89b-12d3-a456-426614174000";

        // ── POST /api/v1/posts/{id}/save ─────────────────────────────────────────

        @Test
        @WithMockUser(username = MOCK_USER_ID)
        void savePost_authenticated_returns204NoContent() throws Exception {
                UUID postId = UUID.randomUUID();
                UUID userId = UUID.fromString(MOCK_USER_ID);
                SavedPost savedPost = SavedPost.of(postId, userId);

                when(savePostUseCase.save(any(SavePostUseCase.Command.class))).thenReturn(savedPost);

                mockMvc.perform(post("/api/v1/posts/{id}/save", postId)
                                .with(csrf()))
                                .andExpect(status().isNoContent());
        }

        @Test
        void savePost_unauthenticated_returns401Or403() throws Exception {
                UUID postId = UUID.randomUUID();

                mockMvc.perform(post("/api/v1/posts/{id}/save", postId)
                                .with(csrf()))
                                .andExpect(status().is3xxRedirection());
        }

        @Test
        @WithMockUser(username = MOCK_USER_ID)
        void savePost_alreadySaved_returns409Conflict() throws Exception {
                UUID postId = UUID.randomUUID();
                UUID userId = UUID.fromString(MOCK_USER_ID);

                when(savePostUseCase.save(any(SavePostUseCase.Command.class)))
                                .thenThrow(new AlreadySavedException(postId, userId));

                mockMvc.perform(post("/api/v1/posts/{id}/save", postId)
                                .with(csrf()))
                                .andExpect(status().isConflict());
        }

        // ── DELETE /api/v1/posts/{id}/save ───────────────────────────────────────

        @Test
        @WithMockUser(username = MOCK_USER_ID)
        void unsavePost_authenticated_returns204NoContent() throws Exception {
                UUID postId = UUID.randomUUID();

                mockMvc.perform(delete("/api/v1/posts/{id}/save", postId)
                                .with(csrf()))
                                .andExpect(status().isNoContent());
        }

        // ── GET /api/v1/users/me/saved ────────────────────────────────────────────

        @Test
        @WithMockUser(username = MOCK_USER_ID)
        void getSavedPosts_authenticated_returns200WithList() throws Exception {
                UUID postId = UUID.randomUUID();
                UUID userId = UUID.fromString(MOCK_USER_ID);
                SavedPost savedPost = SavedPost.builder()
                                .postId(postId)
                                .userId(userId)
                                .savedAt(Instant.now())
                                .build();

                when(getSavedPostsUseCase.getSavedPosts(any(GetSavedPostsUseCase.Query.class)))
                                .thenReturn(new PageImpl<>(List.of(savedPost)));

                mockMvc.perform(get("/api/v1/users/me/saved"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content").isArray())
                                .andExpect(jsonPath("$.data.content[0].postId").value(postId.toString()))
                                .andExpect(jsonPath("$.data.content[0].userId").value(userId.toString()));
        }

        @Test
        void getSavedPosts_unauthenticated_returns401Or403() throws Exception {
                mockMvc.perform(get("/api/v1/users/me/saved"))
                                .andExpect(status().is3xxRedirection());
        }
}
