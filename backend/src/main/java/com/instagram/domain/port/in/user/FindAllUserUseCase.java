package com.instagram.domain.port.in.user;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.User;

public interface FindAllUserUseCase {
    List<User> findAllByIds(Collection<UUID> ids);
}
