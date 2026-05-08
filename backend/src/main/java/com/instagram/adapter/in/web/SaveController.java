package com.instagram.adapter.in.web;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.SavedPostResponse;
import com.instagram.domain.model.SavedPost;
import com.instagram.domain.port.in.save.GetSavedPostsUseCase;
import com.instagram.domain.port.in.save.SavePostUseCase;
import com.instagram.domain.port.in.save.UnsavePostUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@Tag(name = "Saves")
public class SaveController {
    private final SavePostUseCase savePostUseCase;
    private final UnsavePostUseCase unsavePostUseCase;
    private final GetSavedPostsUseCase getSavedPostsUseCase;

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

    @PostMapping("/api/v1/posts/{id}/save")
    public ResponseEntity<ApiResponse<Void>> savePost(@PathVariable UUID id) {
        savePostUseCase.save(new SavePostUseCase.Command(id, currentUserId()));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @DeleteMapping("/api/v1/posts/{id}/save")
    public ResponseEntity<ApiResponse<Void>> unsavePost(@PathVariable UUID id) {
        unsavePostUseCase.unsave(new UnsavePostUseCase.Command(id, currentUserId()));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/api/v1/users/me/saved")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Page<SavedPostResponse>>> getSavedPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<SavedPost> savedPosts = getSavedPostsUseCase.getSavedPosts(
                new GetSavedPostsUseCase.Query(currentUserId(), page, size));
        return ResponseEntity.ok(ApiResponse.ok(savedPosts.map(SavedPostResponse::from)));
    }
}
