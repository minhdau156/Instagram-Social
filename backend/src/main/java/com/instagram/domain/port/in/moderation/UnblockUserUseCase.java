package com.instagram.domain.port.in.moderation;

import java.util.UUID;

public interface UnblockUserUseCase {
    void unblockUser(Command command);

    record Command(
            UUID blockerId,
            String targetUsername) {
    }
}
