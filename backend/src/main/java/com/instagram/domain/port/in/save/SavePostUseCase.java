package com.instagram.domain.port.in.save;

import java.util.UUID;

import com.instagram.domain.model.SavedPost;

public interface SavePostUseCase {
    SavedPost save(Command command);

    record Command(UUID postId, UUID userId) {
    }
}