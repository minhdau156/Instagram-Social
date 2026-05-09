package com.instagram.domain.port.out;

import java.util.UUID;

public interface UserInterestPort {
    void recordLike(UUID userId, UUID postId);

    void recordComment(UUID userId, UUID postId);
}
