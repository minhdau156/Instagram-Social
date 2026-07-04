package com.instagram.adapter.in.web;

import java.util.Collections;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.instagram.adapter.out.persistence.repository.IdempotencyKeyJpaRepository;
import com.instagram.domain.exception.AlreadyLikedException;
import com.instagram.domain.exception.NotLikedException;
import com.instagram.domain.port.in.like.*;
import com.instagram.infrastructure.security.JwtTokenProvider;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;
import com.instagram.infrastructure.security.SecurityConfig;

@WebMvcTest(LikeController.class)
@Import(SecurityConfig.class)
public class LikeControllerIT {
    @Autowired
    MockMvc mockMvc;

    @MockBean
    JwtTokenProvider jwtTokenProvider;
        
    @MockBean
    private UserDetailsService userDetailsService;

    @MockBean
    private OAuth2SuccessHandler oAuth2SuccessHandler;

    @MockBean
    private IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;

    @MockBean
    private LikePostUseCase likePostUseCase;

    @MockBean
    private UnlikePostUseCase unlikePostUseCase;

    @MockBean
    private LikeCommentUseCase likeCommentUseCase;

    @MockBean
    private UnlikeCommentUseCase unlikeCommentUseCase;

    @MockBean
    private GetPostLikersUseCase getPostLikersUseCase;

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void likePost_returns204_onSuccess() throws Exception {
        // Arrange
        var postId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");
        doNothing().when(likePostUseCase).like(any(LikePostUseCase.Command.class));

        // Act & Assert
        mockMvc.perform(post("/api/v1/posts/{id}/like", postId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void likePost_returns409_onAlreadyLikedException() throws Exception {
        // Arrange
        var postId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

        doThrow(new AlreadyLikedException("post", postId)).when(likePostUseCase)
                .like(any(LikePostUseCase.Command.class));

        // Act & Assert
        mockMvc.perform(post("/api/v1/posts/{id}/like", postId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void unlikePost_returns204_onSuccess() throws Exception {
        // Arrange
        var postId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

        doNothing().when(unlikePostUseCase).unlike(any(UnlikePostUseCase.Command.class));

        // Act & Assert
        mockMvc.perform(delete("/api/v1/posts/{id}/like", postId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void unlikePost_returns404_onNotLikedException() throws Exception {
        // Arrange
        var postId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

        doThrow(new NotLikedException("post", postId)).when(unlikePostUseCase)
                .unlike(any(UnlikePostUseCase.Command.class));

        // Act & Assert
        mockMvc.perform(delete("/api/v1/posts/{id}/like", postId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void getPostLikers_returns200_onSuccess() throws Exception {
        // Arrange
        var postId = UUID.fromString("123e4567-e89b-12d3-a456-426614174001");

        when(getPostLikersUseCase.getPostLikers(any()))
    .thenReturn(new PageImpl<>(Collections.emptyList()));

        // Act & Assert
        mockMvc.perform(get("/api/v1/posts/{id}/likers", postId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

}
