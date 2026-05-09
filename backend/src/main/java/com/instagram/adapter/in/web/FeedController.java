package com.instagram.adapter.in.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.FeedPageResponse;
import com.instagram.adapter.in.web.dto.response.TrendingHashtagResponse;
import com.instagram.domain.port.in.feed.GetExploreFeedUseCase;
import com.instagram.domain.port.in.feed.GetHomeFeedUseCase;
import com.instagram.domain.port.out.FeedRepository;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Feed", description = "Home feed and explore endpoints")
@SecurityRequirement(name = "bearerAuth")
public class FeedController {
    private final GetHomeFeedUseCase getHomeFeedUseCase;
    private final GetExploreFeedUseCase getExploreFeedUseCase;
    private final FeedRepository feedRepository;

    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<FeedPageResponse>> getHomeFeed(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {

        UUID userId = currentUserId();
        UUID cursorId = cursor != null ? UUID.fromString(cursor) : null;

        GetHomeFeedUseCase.FeedPage page = getHomeFeedUseCase.getHomeFeed(
                new GetHomeFeedUseCase.Query(userId, cursorId, limit));

        return ResponseEntity.ok(ApiResponse.ok(FeedPageResponse.from(page)));
    }

    @GetMapping("/explore")
    public ResponseEntity<ApiResponse<FeedPageResponse>> getExploreFeed(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {

        UUID userId = currentUserId();
        UUID cursorId = cursor != null ? UUID.fromString(cursor) : null;

        GetExploreFeedUseCase.FeedPage page = getExploreFeedUseCase.getExploreFeed(
                new GetExploreFeedUseCase.Query(userId, cursorId, limit));

        return ResponseEntity.ok(ApiResponse.ok(FeedPageResponse.from(page)));
    }

    @GetMapping("/explore/hashtags")
    public ResponseEntity<ApiResponse<List<TrendingHashtagResponse>>> getTrendingHashtags(
            @RequestParam(defaultValue = "10") int limit) {

        List<TrendingHashtagResponse> hashtags = feedRepository
                .getTrendingHashtags(Math.min(limit, 30))
                .stream()
                .map(TrendingHashtagResponse::from)
                .toList();

        return ResponseEntity.ok(ApiResponse.ok(hashtags));
    }

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
}
