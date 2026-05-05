package com.instagram.adapter.in.web.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.instagram.domain.model.PostShare;
import com.instagram.domain.model.ShareType;

public record ShareResponse(UUID id, UUID postId, UUID sharerId, ShareType shareType, Instant createdAt) {
    public static ShareResponse from(PostShare share) {
        return new ShareResponse(share.getId(), share.getPostId(), share.getSharerId(), share.getShareType(),
                share.getCreatedAt());
    }
}
