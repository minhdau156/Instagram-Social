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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.UserSummaryResponse;
import com.instagram.domain.model.UserSummary;
import com.instagram.domain.port.in.like.GetPostLikersUseCase;
import com.instagram.domain.port.in.like.LikeCommentUseCase;
import com.instagram.domain.port.in.like.LikePostUseCase;
import com.instagram.domain.port.in.like.UnlikeCommentUseCase;
import com.instagram.domain.port.in.like.UnlikePostUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Likes", description = "Operations related to likes")
public class LikeController {
    private final LikePostUseCase likePostUseCase;
    private final UnlikePostUseCase unlikePostUseCase;
    private final GetPostLikersUseCase getPostLikersUseCase;
    private final LikeCommentUseCase likeCommentUseCase;
    private final UnlikeCommentUseCase unlikeCommentUseCase;

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

    @PostMapping("/api/v1/posts/{id}/like")
    public ResponseEntity<ApiResponse<Void>> likePost(@PathVariable UUID id) {
        likePostUseCase.like(new LikePostUseCase.Command(id, currentUserId()));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/api/v1/posts/{id}/like")
    public ResponseEntity<ApiResponse<Void>> unlikePost(@PathVariable UUID id) {
        unlikePostUseCase.unlike(new UnlikePostUseCase.Command(id, currentUserId()));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/api/v1/posts/{id}/likers")
    public ResponseEntity<ApiResponse<Page<UserSummaryResponse>>> getPostLikers(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<UserSummary> likers = getPostLikersUseCase.getPostLikers(new GetPostLikersUseCase.Query(
                id, currentUserId(), page, size));
        return ResponseEntity.ok(ApiResponse.ok(likers.map(UserSummaryResponse::from)));
    }

    @PostMapping("/api/v1/comments/{id}/like")
    public ResponseEntity<ApiResponse<Void>> likeComment(@PathVariable UUID id) {
        likeCommentUseCase.likeComment(new LikeCommentUseCase.Command(id, currentUserId()));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/api/v1/comments/{id}/like")
    public ResponseEntity<ApiResponse<Void>> unlikeComment(@PathVariable UUID id) {
        unlikeCommentUseCase.unlikeComment(new UnlikeCommentUseCase.Command(id, currentUserId()));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

}
