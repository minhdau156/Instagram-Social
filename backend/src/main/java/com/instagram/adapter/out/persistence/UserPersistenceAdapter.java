package com.instagram.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.repository.UserJpaRepository;
import com.instagram.domain.model.User;
import com.instagram.domain.port.out.UserRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserPersistenceAdapter implements UserRepository {

    private final UserJpaRepository jpaRepository;

    // ── UserRepository (out-port) ────────────────────────────────────────────

    @Override
    public User save(User user) {
        UserJpaEntity entity = toEntity(user);
        UserJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<User> findAllByIds(Collection<UUID> ids) {
        return jpaRepository.findAllById(ids).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<User> findByUsername(String username) {
        return jpaRepository.findByUsername(username).map(this::toDomain);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return jpaRepository.findByEmail(email).map(this::toDomain);
    }

    @Override
    public List<User> findAll(int page, int size) {
        return jpaRepository.findAll(PageRequest.of(page, size))
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<User> searchByUsername(String term, int limit) {
        return jpaRepository
                .findByUsernameContainingIgnoreCase(term, PageRequest.of(0, limit, Sort.by("username")))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsByUsername(String username) {
        return jpaRepository.existsByUsername(username);
    }

    @Override
    public boolean existsByEmail(String email) {
        return jpaRepository.existsByEmail(email);
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private UserJpaEntity toEntity(User user) {
        return UserJpaEntity.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phoneNumber(user.getPhoneNumber())
                .passwordHash(user.getPasswordHash())
                .fullName(user.getFullName())
                .bio(user.getBio())
                .profilePictureUrl(user.getProfilePictureUrl())
                .websiteUrl(user.getWebsiteUrl())
                .privacyLevel(user.getPrivacyLevel())
                .isVerified(user.isVerified())
                .status(user.getStatus())
                .lastLoginAt(user.getLastLoginAt())
                .build();
    }

    private User toDomain(UserJpaEntity entity) {
        return User.builder()
                .id(entity.getId())
                .username(entity.getUsername())
                .email(entity.getEmail())
                .phoneNumber(entity.getPhoneNumber())
                .passwordHash(entity.getPasswordHash())
                .fullName(entity.getFullName())
                .bio(entity.getBio())
                .profilePictureUrl(entity.getProfilePictureUrl())
                .websiteUrl(entity.getWebsiteUrl())
                .privacyLevel(entity.getPrivacyLevel())
                .isVerified(entity.isVerified())
                .status(entity.getStatus())
                .lastLoginAt(entity.getLastLoginAt())
                .build();
    }
}
