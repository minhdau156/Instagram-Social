package com.instagram.adapter.in.web;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.adapter.in.web.dto.request.ReportRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.BlockedUserResponse;
import com.instagram.adapter.in.web.dto.response.ReportResponse;
import com.instagram.domain.model.Report;
import com.instagram.domain.model.User;
import com.instagram.domain.model.UserBlock;
import com.instagram.domain.port.in.moderation.BlockUserUseCase;
import com.instagram.domain.port.in.moderation.GetBlockedUsersUseCase;
import com.instagram.domain.port.in.moderation.ReportContentUseCase;
import com.instagram.domain.port.in.moderation.UnblockUserUseCase;
import com.instagram.domain.port.in.user.FindAllUserUseCase;
import com.instagram.domain.port.in.user.GetUserUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Moderation", description = "Endpoints for moderation")
@AllArgsConstructor
public class ModerationController {

    private final ReportContentUseCase reportContentUseCase;
    private final BlockUserUseCase blockUserUseCase;
    private final UnblockUserUseCase unblockUserUseCase;
    private final GetBlockedUsersUseCase getBlockedUsersUseCase;
    private final GetUserUseCase getUserUseCase;
    private final FindAllUserUseCase findAllUserUseCase;

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

    @PostMapping("/reports")
    public ResponseEntity<ApiResponse<ReportResponse>> reportContent(
            @Valid @RequestBody ReportRequest request) {
        UUID userId = currentUserId();
        Report report = this.reportContentUseCase.reportContent(new ReportContentUseCase.Command(
                userId,
                request.entityType(),
                request.entityId(),
                request.reason().name(),
                request.details()));
        log.info("Content reported userId={} entityType={} entityId={}", userId, request.entityType(), request.entityId());

        User reporter = this.getUserUseCase.getUser(new GetUserUseCase.Query(userId));

        return ResponseEntity.ok(ApiResponse.ok(ReportResponse.from(report, reporter)));
    }

    @PostMapping("users/{username}/block")
    public ResponseEntity<ApiResponse<Void>> blockUser(@PathVariable String username) {
        this.blockUserUseCase.blockUser(new BlockUserUseCase.Command(
                currentUserId(),
                username));
        log.info("User blocked username={}", username);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("users/{username}/block")
    public ResponseEntity<ApiResponse<Void>> unblockUser(@PathVariable String username) {
        this.unblockUserUseCase.unblockUser(new UnblockUserUseCase.Command(
                currentUserId(),
                username));
        log.info("User unblocked username={}", username);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("users/me/blocked")
    public ResponseEntity<ApiResponse<List<BlockedUserResponse>>> getBlockedUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        log.debug("getBlockedUsers page={} size={}", page, size);
        List<UserBlock> blockedUsers = this.getBlockedUsersUseCase.getBlockedUsers(
                new GetBlockedUsersUseCase.Query(
                        currentUserId(),
                        page,
                        size));

        if (CollectionUtils.isEmpty(blockedUsers)) {
            return ResponseEntity.ok(ApiResponse.ok(Collections.emptyList()));
        }

        Collection<UUID> ids = blockedUsers.stream()
                .map(UserBlock::getBlockedId)
                .collect(Collectors.toList());
        List<User> blockedUsersDomain = this.findAllUserUseCase.findAllByIds(ids);
        Map<UUID, User> userMap = blockedUsersDomain.stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        List<BlockedUserResponse> blockedUserResponses = blockedUsers.stream()
                .map(blockedUser -> BlockedUserResponse.from(blockedUser, userMap.get(blockedUser.getBlockedId())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(blockedUserResponses));
    }

}
