package com.instagram.domain.port.out;

import java.util.UUID;

public interface PostHashtagRepository {
    void save(UUID postId, UUID hashtagId);
}
