package com.instagram.domain.port.in.user;

import java.util.UUID;

import com.instagram.domain.model.User;

public interface GetUserUseCase {
    User getUser(Query query);

    record Query(UUID userId) {

    }

}
