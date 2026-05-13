package com.instagram.domain.port.out;

import com.instagram.domain.model.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);

    Optional<User> findById(UUID id);

    /** Batch-fetch users by a collection of IDs — single query, eliminates N+1. */
    List<User> findAllByIds(Collection<UUID> ids);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    List<User> findAll(int page, int size);

    List<User> searchByUsername(String term, int limit);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
