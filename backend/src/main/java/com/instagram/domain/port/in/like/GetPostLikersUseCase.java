package com.instagram.domain.port.in.like;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import com.instagram.domain.model.UserSummary;

public interface GetPostLikersUseCase {
    Page<UserSummary> getPostLikers(Query query);

    record Query(UUID postId, UUID requestingUserId, int page, int size) {
    }
}
