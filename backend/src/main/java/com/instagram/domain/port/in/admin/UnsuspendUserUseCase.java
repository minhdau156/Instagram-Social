package com.instagram.domain.port.in.admin;

import java.util.UUID;

import com.instagram.domain.model.User;

public interface UnsuspendUserUseCase {
    User unsuspendUser(Command command);

    record Command(
            UUID adminId,
            UUID targetUserId) {
    }
}
