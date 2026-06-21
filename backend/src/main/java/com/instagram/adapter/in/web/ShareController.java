package com.instagram.adapter.in.web;

import java.util.UUID;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.adapter.in.web.dto.request.ShareRequest;
import com.instagram.adapter.in.web.dto.response.ShareResponse;
import com.instagram.domain.model.PostShare;
import com.instagram.domain.port.in.share.SharePostUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "Shares")
public class ShareController {

    private final SharePostUseCase sharePostUseCase;

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

    @PostMapping("/api/v1/posts/{id}/share")
    public ShareResponse sharePost(@PathVariable UUID id, @RequestBody ShareRequest request) {
        PostShare share = sharePostUseCase
                .share(new SharePostUseCase.Command(id, currentUserId(), request.recipientId(), request.shareType()));
        log.info("Post shared postId={}", id);
        return ShareResponse.from(share);
    }
}
