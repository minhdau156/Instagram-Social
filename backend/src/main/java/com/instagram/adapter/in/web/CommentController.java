package com.instagram.adapter.in.web;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.adapter.in.web.dto.request.AddCommentRequest;
import com.instagram.adapter.in.web.dto.request.EditCommentRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.CommentResponse;
import com.instagram.domain.model.Comment;
import com.instagram.domain.model.User;
import com.instagram.domain.port.in.comment.AddCommentUseCase;
import com.instagram.domain.port.in.comment.DeleteCommentUseCase;
import com.instagram.domain.port.in.comment.EditCommentUseCase;
import com.instagram.domain.port.in.comment.GetCommentsUseCase;
import com.instagram.domain.port.in.comment.GetRepliesUseCase;
import com.instagram.domain.port.in.user.GetUserUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Comments", description = "Comments API")
public class CommentController {
    private final GetUserUseCase getUserUseCase;
    private final AddCommentUseCase addCommentUseCase;
    private final EditCommentUseCase editCommentUseCase;
    private final DeleteCommentUseCase deleteCommentUseCase;
    private final GetCommentsUseCase getCommentsUseCase;
    private final GetRepliesUseCase getRepliesUseCase;

    @Nullable
    private UUID currentUserIdOrNull() {
        org.springframework.security.core.Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return null;
        }
        if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails userDetails) {
            return UUID.fromString(userDetails.getUsername());
        }
        return UUID.fromString(auth.getPrincipal().toString());
    }

    private UUID currentUserId() {
        UUID userId = currentUserIdOrNull();
        if (userId == null) {
            throw new IllegalStateException("User is not authenticated");
        }
        return userId;
    }

    @PostMapping("/api/v1/posts/{id}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable UUID id,
            @Valid @RequestBody AddCommentRequest request) {
        Comment comment = addCommentUseCase
                .addComment(new AddCommentUseCase.Command(id, currentUserId(), request.content(), request.parentId()));

        User user = getUserUseCase.getUser(new GetUserUseCase.Query(currentUserId()));

        CommentResponse commentResponse = CommentResponse.from(comment, user);

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(commentResponse));
    }

    @PutMapping("/api/v1/comments/{id}")
    public ResponseEntity<ApiResponse<CommentResponse>> editComment(
            @PathVariable UUID id,
            @Valid @RequestBody EditCommentRequest request) {
        Comment comment = editCommentUseCase
                .editComment(new EditCommentUseCase.Command(id, currentUserId(), request.content()));

        User user = getUserUseCase.getUser(new GetUserUseCase.Query(currentUserId()));

        CommentResponse commentResponse = CommentResponse.from(comment, user);

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok(commentResponse));
    }

    @DeleteMapping("/api/v1/comments/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable UUID id) {
        deleteCommentUseCase.deleteComment(new DeleteCommentUseCase.Command(id, currentUserId()));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/api/v1/posts/{id}/comments")
    public ResponseEntity<ApiResponse<Page<CommentResponse>>> getComments(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Comment> comments = getCommentsUseCase
                .getComments(new GetCommentsUseCase.Query(id, currentUserId(), page, size));

        Page<CommentResponse> commentResponses = comments.map(comment -> {
            User user = getUserUseCase.getUser(new GetUserUseCase.Query(comment.getUserId()));
            return CommentResponse.from(comment, user);
        });

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok(commentResponses));

    }

    @GetMapping("/api/v1/comments/{id}/replies")
    public ResponseEntity<ApiResponse<Page<CommentResponse>>> getReplies(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Page<Comment> comments = getRepliesUseCase
                .getReplies(new GetRepliesUseCase.Query(id, currentUserId(), page, size));

        Page<CommentResponse> commentResponses = comments.map(comment -> {
            User user = getUserUseCase.getUser(new GetUserUseCase.Query(comment.getUserId()));
            return CommentResponse.from(comment, user);
        });

        return ResponseEntity.status(HttpStatus.OK).body(ApiResponse.ok(commentResponses));

    }

}
