package com.instagram.adapter.out.persistence.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.instagram.adapter.out.persistence.entity.UserStatsJpaEntity;

import jakarta.transaction.Transactional;

public interface UserStatsJpaRepository extends JpaRepository<UserStatsJpaEntity, UUID> {
        Optional<UserStatsJpaEntity> findByUserId(UUID userId);

        // Increment follower count of the target user
        @Modifying(clearAutomatically = true)
        @Transactional
        @Query("""
                        UPDATE UserStatsJpaEntity us
                        SET us.followerCount = us.followerCount + 1
                        WHERE us.userId = :userId
                        """)
        void incrementFollowerCount(@Param("userId") UUID userId);

        // Decrement follower count of the target user
        @Modifying(clearAutomatically = true)
        @Transactional
        @Query("""
                        UPDATE UserStatsJpaEntity us
                        SET us.followerCount = us.followerCount - 1
                        WHERE us.userId = :userId
                        """)
        void decrementFollowerCount(@Param("userId") UUID userId);

        // Increment following count of the user who followed
        @Modifying(clearAutomatically = true)
        @Transactional
        @Query("""
                        UPDATE UserStatsJpaEntity us
                        SET us.followingCount = us.followingCount + 1
                        WHERE us.userId = :userId
                        """)
        void incrementFollowingCount(@Param("userId") UUID userId);

        // Decrement following count of the user who unfollowed
        @Modifying(clearAutomatically = true)
        @Transactional
        @Query("""
                        UPDATE UserStatsJpaEntity us
                        SET us.followingCount = us.followingCount - 1
                        WHERE us.userId = :userId
                        """)
        void decrementFollowingCount(@Param("userId") UUID userId);

        // Increment post count when creating a post
        @Modifying(clearAutomatically = true)
        @Transactional
        @Query("""
                        UPDATE UserStatsJpaEntity us
                        SET us.postCount = us.postCount + 1
                        WHERE us.userId = :userId
                        """)
        void incrementPostCount(@Param("userId") UUID userId);

        // Decrement post count when deleting a post
        @Modifying(clearAutomatically = true)
        @Transactional
        @Query("""
                        UPDATE UserStatsJpaEntity us
                        SET us.postCount = us.postCount - 1
                        WHERE us.userId = :userId
                        """)
        void decrementPostCount(@Param("userId") UUID userId);

        List<UserStatsJpaEntity> findByUserIdIn(Collection<UUID> userIds);
}
