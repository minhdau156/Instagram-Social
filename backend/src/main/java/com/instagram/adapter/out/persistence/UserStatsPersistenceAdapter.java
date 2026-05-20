package com.instagram.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.UserStatsJpaEntity;
import com.instagram.adapter.out.persistence.repository.UserStatsJpaRepository;
import com.instagram.domain.model.UserStats;
import com.instagram.domain.port.out.UserStatsRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class UserStatsPersistenceAdapter implements UserStatsRepository {

    private final UserStatsJpaRepository jpaRepository;

    @Override
    public Optional<UserStats> findByUserId(UUID userId) {
        return jpaRepository.findByUserId(userId).map(this::toDomain);
    }

    @Override
    public void create(UserStats userStats) {
        UserStatsJpaEntity userStatsJpaEntity = toEntity(userStats);
        jpaRepository.save(userStatsJpaEntity);
    }

    @Override
    public void incrementFollowerCount(UUID userId) {
        if (!this.findByUserId(userId).isPresent()) {
            this.create(new UserStats(userId, 0, 0, 0));
        }
        this.jpaRepository.incrementFollowerCount(userId);
    }

    @Override
    public void decrementFollowerCount(UUID userId) {
        if (!this.findByUserId(userId).isPresent()) {
            this.create(new UserStats(userId, 0, 0, 0));
        }
        this.jpaRepository.decrementFollowerCount(userId);
    }

    @Override
    public void incrementFollowingCount(UUID userId) {
        if (!this.findByUserId(userId).isPresent()) {
            this.create(new UserStats(userId, 0, 0, 0));
        }
        this.jpaRepository.incrementFollowingCount(userId);
    }

    @Override
    public void decrementFollowingCount(UUID userId) {
        if (!this.findByUserId(userId).isPresent()) {
            this.create(new UserStats(userId, 0, 0, 0));
        }
        this.jpaRepository.decrementFollowingCount(userId);
    }

    private UserStatsJpaEntity toEntity(UserStats userStats) {
        return UserStatsJpaEntity.builder()
                .userId(userStats.userId())
                .followerCount(userStats.followerCount())
                .followingCount(userStats.followingCount())
                .postCount(userStats.postCount())
                .build();
    }

    private UserStats toDomain(UserStatsJpaEntity userStatsJpaEntity) {
        return new UserStats(
                userStatsJpaEntity.getUserId(),
                userStatsJpaEntity.getPostCount(),
                userStatsJpaEntity.getFollowerCount(),
                userStatsJpaEntity.getFollowingCount());
    }

    @Override
    public List<UserStats> findAllByIds(Collection<UUID> userIds) {
        return jpaRepository.findByUserIdIn(userIds)
                .stream()
                .map(this::toDomain)
                .toList();
    }

}
