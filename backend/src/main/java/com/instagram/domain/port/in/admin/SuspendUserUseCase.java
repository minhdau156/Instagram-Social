package com.instagram.domain.port.in.admin;

import java.util.UUID;

import com.instagram.domain.model.User;

public interface SuspendUserUseCase {
    User suspendUser(Command command);

    record Command(
            UUID adminId,
            UUID targetUserId,
            String reason) {
    }
}
