package com.instagram.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.instagram.adapter.in.web.dto.request.AddCommentRequest;
import com.instagram.adapter.in.web.dto.request.EditCommentRequest;
import com.instagram.domain.exception.UnauthorizedCommentAccessException;
import com.instagram.domain.model.Comment;
import com.instagram.domain.model.User;
import com.instagram.domain.port.in.comment.AddCommentUseCase;
import com.instagram.domain.port.in.comment.DeleteCommentUseCase;
import com.instagram.domain.port.in.comment.EditCommentUseCase;
import com.instagram.domain.port.in.comment.GetCommentsUseCase;
import com.instagram.domain.port.in.comment.GetRepliesUseCase;
import com.instagram.domain.port.in.user.GetUserUseCase;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import com.instagram.infrastructure.security.SecurityConfig;
import com.instagram.infrastructure.security.JwtTokenProvider;
import org.springframework.security.core.userdetails.UserDetailsService;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;

@WebMvcTest(CommentController.class)
@Import(SecurityConfig.class)
class CommentControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @Autowired
        private ObjectMapper objectMapper;

        @MockBean
        private JwtTokenProvider jwtTokenProvider;

        @MockBean
        private UserDetailsService userDetailsService;

        @MockBean
        private OAuth2SuccessHandler oAuth2SuccessHandler;

        @MockBean
        private GetUserUseCase getUserUseCase;

        @MockBean
        private AddCommentUseCase addCommentUseCase;

        @MockBean
        private EditCommentUseCase editCommentUseCase;

        @MockBean
        private DeleteCommentUseCase deleteCommentUseCase;

        @MockBean
        private GetCommentsUseCase getCommentsUseCase;

        @MockBean
        private GetRepliesUseCase getRepliesUseCase;

        @Test
        @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
        void getComments_withValidPostId_returns200OK() throws Exception {
                UUID postId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                Comment comment = Comment.builder()
                                .id(UUID.randomUUID())
                                .postId(postId)
                                .userId(userId)
                                .content("Test Comment")
                                .status(com.instagram.domain.model.CommentStatus.ACTIVE)
                                .build();
                User user = User.builder().id(userId).username("testuser").build();

                when(getCommentsUseCase.getComments(any(GetCommentsUseCase.Query.class)))
                                .thenReturn(new PageImpl<>(List.of(comment)));
                when(getUserUseCase.getUser(any(GetUserUseCase.Query.class))).thenReturn(user);

                mockMvc.perform(get("/api/v1/posts/{id}/comments", postId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content").isArray())
                                .andExpect(jsonPath("$.data.content[0].content").value("Test Comment"))
                                .andExpect(jsonPath("$.data.content[0].username").value("testuser"));
        }

        @Test
        @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
        void addComment_withValidPostIdAndContent_returns201Created() throws Exception {
                UUID postId = UUID.randomUUID();
                UUID userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
                AddCommentRequest request = new AddCommentRequest("New Comment", null);

                Comment comment = Comment.builder()
                                .id(UUID.randomUUID())
                                .postId(postId)
                                .userId(userId)
                                .content("New Comment")
                                .status(com.instagram.domain.model.CommentStatus.ACTIVE)
                                .build();
                User user = User.builder().id(userId).username("testuser").build();

                when(addCommentUseCase.addComment(any(AddCommentUseCase.Command.class))).thenReturn(comment);
                when(getUserUseCase.getUser(any(GetUserUseCase.Query.class))).thenReturn(user);

                mockMvc.perform(post("/api/v1/posts/{id}/comments", postId)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.content").value("New Comment"))
                                .andExpect(jsonPath("$.data.username").value("testuser"));
        }

        @Test
        @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
        void addComment_emptyContent_returns400BadRequest() throws Exception {
                UUID postId = UUID.randomUUID();
                AddCommentRequest request = new AddCommentRequest("", null);

                mockMvc.perform(post("/api/v1/posts/{id}/comments", postId)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isBadRequest());
        }

        @Test
        void addComment_unauthenticated_returns401Or403() throws Exception {
                UUID postId = UUID.randomUUID();
                AddCommentRequest request = new AddCommentRequest("New Comment", null);

                mockMvc.perform(post("/api/v1/posts/{id}/comments", postId)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isUnauthorized()); // Or isForbidden depending on SecurityConfig.
                                                                       // Let's accept isUnauthorized as typically
                                                                       // we'd map this, or 403 from spring security
        }

        @Test
        @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
        void editComment_owner_returns200OK() throws Exception {
                UUID commentId = UUID.randomUUID();
                UUID userId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
                EditCommentRequest request = new EditCommentRequest("Updated Comment");

                Comment comment = Comment.builder()
                                .id(commentId)
                                .postId(UUID.randomUUID())
                                .userId(userId)
                                .content("Updated Comment")
                                .status(com.instagram.domain.model.CommentStatus.ACTIVE)
                                .build();
                User user = User.builder().id(userId).username("testuser").build();

                when(editCommentUseCase.editComment(any(EditCommentUseCase.Command.class))).thenReturn(comment);
                when(getUserUseCase.getUser(any(GetUserUseCase.Query.class))).thenReturn(user);

                mockMvc.perform(put("/api/v1/comments/{id}", commentId)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content").value("Updated Comment"));
        }

        @Test
        @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
        void editComment_nonOwner_returns403Forbidden() throws Exception {
                UUID commentId = UUID.randomUUID();
                EditCommentRequest request = new EditCommentRequest("Updated Comment");

                when(editCommentUseCase.editComment(any(EditCommentUseCase.Command.class)))
                                .thenThrow(new UnauthorizedCommentAccessException(commentId, UUID.randomUUID()));

                mockMvc.perform(put("/api/v1/comments/{id}", commentId)
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                                .andExpect(status().isForbidden());
        }

        @Test
        @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
        void deleteComment_owner_returns204NoContent() throws Exception {
                UUID commentId = UUID.randomUUID();

                mockMvc.perform(delete("/api/v1/comments/{id}", commentId)
                                .with(csrf()))
                                .andExpect(status().isNoContent());
        }

        @Test
        @WithMockUser(username = "123e4567-e89b-12d3-a456-426614174000")
        void getReplies_withValidCommentId_returns200OK() throws Exception {
                UUID commentId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                Comment comment = Comment.builder()
                                .id(UUID.randomUUID())
                                .postId(UUID.randomUUID())
                                .userId(userId)
                                .parentId(commentId)
                                .content("Test Reply")
                                .status(com.instagram.domain.model.CommentStatus.ACTIVE)
                                .build();
                User user = User.builder().id(userId).username("testuser").build();

                when(getRepliesUseCase.getReplies(any(GetRepliesUseCase.Query.class)))
                                .thenReturn(new PageImpl<>(List.of(comment)));
                when(getUserUseCase.getUser(any(GetUserUseCase.Query.class))).thenReturn(user);

                mockMvc.perform(get("/api/v1/comments/{id}/replies", commentId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data.content").isArray())
                                .andExpect(jsonPath("$.data.content[0].content").value("Test Reply"));
        }
}
