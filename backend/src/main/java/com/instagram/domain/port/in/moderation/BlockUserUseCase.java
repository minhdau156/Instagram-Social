package com.instagram.domain.port.in.moderation;

import java.util.UUID;

import com.instagram.domain.model.UserBlock;

public interface BlockUserUseCase {
    UserBlock blockUser(Command command);

    record Command(
            UUID blockerId,
            String targetUsername) {
    }
}
