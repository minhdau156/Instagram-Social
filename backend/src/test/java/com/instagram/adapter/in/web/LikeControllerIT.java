package com.instagram.adapter.in.web;

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
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.instagram.domain.exception.AlreadyLikedException;
import com.instagram.domain.exception.NotLikedException;
import com.instagram.domain.port.in.like.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
public class LikeControllerIT {
    @Autowired
    MockMvc mockMvc;

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

        // Act & Assert
        mockMvc.perform(get("/api/v1/posts/{id}/likers", postId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

}
