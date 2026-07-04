package com.instagram.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

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
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instagram.adapter.in.web.dto.request.CreatePostRequest;
import com.instagram.adapter.in.web.dto.request.MediaItemRequest;
import com.instagram.adapter.in.web.dto.request.UpdatePostRequest;
import com.instagram.adapter.out.persistence.repository.IdempotencyKeyJpaRepository;
import com.instagram.domain.exception.UnauthorizedPostAccessException;
import com.instagram.domain.model.Post;
import com.instagram.domain.port.in.CreatePostUseCase;
import com.instagram.domain.port.in.DeletePostUseCase;
import com.instagram.domain.port.in.GenerateUploadUrlUseCase;
import com.instagram.domain.port.in.GetPostUseCase;
import com.instagram.domain.port.in.GetUserPostsUseCase;
import com.instagram.domain.port.in.UpdatePostUseCase;
import com.instagram.infrastructure.security.HtmlSanitizer;
import com.instagram.infrastructure.security.JwtTokenProvider;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;
import com.instagram.infrastructure.security.SecurityConfig;

@WebMvcTest(PostController.class)
@Import(SecurityConfig.class)
public class PostControllerIT {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    JwtTokenProvider jwtTokenProvider;

    @MockBean
    UserDetailsService userDetailsService;

    @MockBean
    OAuth2SuccessHandler oAuth2SuccessHandler;

    @MockBean
    HtmlSanitizer htmlSanitizer;

    @MockBean
    CreatePostUseCase createPostUseCase;

    @MockBean
    GetPostUseCase getPostUseCase;

    @MockBean
    UpdatePostUseCase updatePostUseCase;

    @MockBean
    DeletePostUseCase deletePostUseCase;

    @MockBean
    GetUserPostsUseCase getUserPostsUseCase;

    @MockBean
    GenerateUploadUrlUseCase generateUploadUrlUseCase;

    @MockBean
    private IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void createPost_returns201_onSuccess() throws Exception {
        var request = new CreatePostRequest("test caption", "test location",
                List.of(new MediaItemRequest("key1", "IMAGE", 1080, 1080, 0, 1024L, "0")));

        var post = Post.builder()
                .id(UUID.randomUUID())
                .userId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
                .caption("test caption")
                .status(com.instagram.domain.model.PostStatus.PUBLISHED)
                .createdAt(java.time.OffsetDateTime.now())
                .updatedAt(java.time.OffsetDateTime.now())
                .build();

        when(createPostUseCase.createPost(any(CreatePostUseCase.Command.class))).thenReturn(post);

        mockMvc.perform(post("/api/v1/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andDo(MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.caption").value("test caption"));
    }

    @Test
    void createPost_unauthenticated_returns401() throws Exception {
        var request = new CreatePostRequest("test caption", "test location",
                List.of(new MediaItemRequest("key1", "IMAGE", 1080, 1080, 0, 1024L, "0")));

        mockMvc.perform(post("/api/v1/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void getPost_returns200_onSuccess() throws Exception {
        var postId = UUID.randomUUID();
        var post = Post.builder()
                .id(postId)
                .userId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
                .caption("test caption")
                .status(com.instagram.domain.model.PostStatus.PUBLISHED)
                .createdAt(java.time.OffsetDateTime.now())
                .updatedAt(java.time.OffsetDateTime.now())
                .build();

        when(getPostUseCase.getPost(any(GetPostUseCase.Query.class))).thenReturn(post);

        mockMvc.perform(get("/api/v1/posts/{id}", postId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(postId.toString()));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void getUserPosts_returns200_onSuccess() throws Exception {
        var userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        var post = Post.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .caption("test caption")
                .status(com.instagram.domain.model.PostStatus.PUBLISHED)
                .createdAt(java.time.OffsetDateTime.now())
                .updatedAt(java.time.OffsetDateTime.now())
                .build();

        when(getUserPostsUseCase.getUserPosts(any(GetUserPostsUseCase.Query.class)))
                .thenReturn(new PageImpl<>(List.of(post)));

        mockMvc.perform(get("/api/v1/posts/users/{userId}/posts", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(post.getId().toString()));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void updatePost_returns200_onSuccess() throws Exception {
        var postId = UUID.randomUUID();
        var request = new UpdatePostRequest("updated caption", "updated location");
        var post = Post.builder()
                .id(postId)
                .userId(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))
                .caption("updated caption")
                .status(com.instagram.domain.model.PostStatus.PUBLISHED)
                .createdAt(java.time.OffsetDateTime.now())
                .updatedAt(java.time.OffsetDateTime.now())
                .build();

        when(updatePostUseCase.updatePost(any(UpdatePostUseCase.Command.class))).thenReturn(post);

        mockMvc.perform(put("/api/v1/posts/{id}", postId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.caption").value("updated caption"));
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void updatePost_nonOwner_returns403Forbidden() throws Exception {
        var postId = UUID.randomUUID();
        var request = new UpdatePostRequest("updated caption", "updated location");

        when(updatePostUseCase.updatePost(any(UpdatePostUseCase.Command.class)))
                .thenThrow(new UnauthorizedPostAccessException(postId,
                        UUID.fromString("123e4567-e89b-12d3-a456-426614174000")));

        mockMvc.perform(put("/api/v1/posts/{id}", postId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void deletePost_returns204_onSuccess() throws Exception {
        var postId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/posts/{id}", postId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
    void deletePost_nonOwner_returns403Forbidden() throws Exception {
        var postId = UUID.randomUUID();

        doThrow(new UnauthorizedPostAccessException(postId,
                UUID.fromString("123e4567-e89b-12d3-a456-426614174000")))
                .when(deletePostUseCase).deletePost(any(DeletePostUseCase.Command.class));

        mockMvc.perform(delete("/api/v1/posts/{id}", postId))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").isNotEmpty());
    }
}
