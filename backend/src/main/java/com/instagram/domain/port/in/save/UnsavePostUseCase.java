package com.instagram.domain.port.in.save;

import java.util.UUID;

public interface UnsavePostUseCase {
    void unsave(Command command);

    record Command(UUID postId, UUID userId) {
    }
}
