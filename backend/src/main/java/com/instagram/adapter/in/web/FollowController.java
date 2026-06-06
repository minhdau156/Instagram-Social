package com.instagram.adapter.in.web;

import java.util.List;
import java.util.UUID;

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
import com.instagram.adapter.in.web.dto.response.CursorPageResponse;
import com.instagram.adapter.in.web.dto.response.FollowResponse;
import com.instagram.adapter.in.web.dto.response.UserSummaryResponse;
import com.instagram.domain.model.Follow;
import com.instagram.domain.model.UserSummary;
import com.instagram.domain.port.in.follow.*;

import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class FollowController {
    private final FollowUserUseCase followUserUseCase;
    private final UnfollowUserUseCase unfollowUserUseCase;
    private final GetFollowersUseCase getFollowersUseCase;
    private final GetFollowingUseCase getFollowingUseCase;
    private final GetFollowRequestsUseCase getFollowRequestsUseCase;
    private final ApproveFollowRequestUseCase approveFollowRequestUseCase;
    private final DeclineFollowRequestUseCase declineFollowRequestUseCase;

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

    @PostMapping("/api/v1/users/{username}/follow")
    public ResponseEntity<ApiResponse<FollowResponse>> followUser(@PathVariable String username) {
        Follow follow = followUserUseCase.follow(new FollowUserUseCase.Command(currentUserId(), username));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(FollowResponse.from(follow)));
    }

    @DeleteMapping("/api/v1/users/{username}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollowUser(@PathVariable String username) {
        unfollowUserUseCase.unfollow(new UnfollowUserUseCase.Command(currentUserId(), username));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    @GetMapping("/api/v1/users/{username}/followers")
    public ResponseEntity<ApiResponse<CursorPageResponse<UserSummaryResponse>>> getFollowers(
            @PathVariable String username,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        GetFollowersUseCase.FollowersPage result = getFollowersUseCase.getFollowers(
                new GetFollowersUseCase.Query(username, currentUserId(), cursor, size));
        List<UserSummaryResponse> items = result.items().stream()
                .map(UserSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(CursorPageResponse.of(items, result.nextCursor())));
    }

    @GetMapping("/api/v1/users/{username}/following")
    public ResponseEntity<ApiResponse<CursorPageResponse<UserSummaryResponse>>> getFollowing(
            @PathVariable String username,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int size) {
        GetFollowingUseCase.FollowingPage result = getFollowingUseCase.getFollowing(
                new GetFollowingUseCase.Query(username, currentUserId(), cursor, size));
        List<UserSummaryResponse> items = result.items().stream()
                .map(UserSummaryResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(CursorPageResponse.of(items, result.nextCursor())));
    }

    @GetMapping("/api/v1/follow-requests")
    public ResponseEntity<ApiResponse<List<UserSummaryResponse>>> getFollowRequests() {
        List<UserSummary> requests = getFollowRequestsUseCase.getFollowRequests(new GetFollowRequestsUseCase.Query(
                currentUserId()));
        return ResponseEntity.ok(ApiResponse.ok(requests.stream()
                .map(UserSummaryResponse::from)
                .toList()));
    }

    @PostMapping("/api/v1/follow-requests/{id}/approve")
    public ResponseEntity<ApiResponse<FollowResponse>> approveFollowRequest(@PathVariable UUID id) {
        Follow follow = approveFollowRequestUseCase
                .approve(new ApproveFollowRequestUseCase.Command(currentUserId(), id));
        return ResponseEntity.ok(ApiResponse.ok(FollowResponse.from(follow)));
    }

    @DeleteMapping("/api/v1/follow-requests/{id}/decline")
    public ResponseEntity<ApiResponse<Void>> declineFollowRequest(@PathVariable UUID id) {
        declineFollowRequestUseCase.decline(new DeclineFollowRequestUseCase.Command(currentUserId(), id));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

}