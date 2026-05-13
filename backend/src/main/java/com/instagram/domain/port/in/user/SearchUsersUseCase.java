package com.instagram.domain.port.in.user;

import com.instagram.domain.model.User;

import java.util.List;

public interface SearchUsersUseCase {
    List<User> searchUsers(Command command);

    record Command(String term, int limit) {}
}
