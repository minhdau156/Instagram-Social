package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.adapter.out.persistence.entity.FollowId;
import com.instagram.adapter.out.persistence.entity.FollowJpaEntity;

public interface FollowJpaRepository extends JpaRepository<FollowJpaEntity, FollowId> {

    Optional<FollowJpaEntity> findByIdFollowerIdAndIdFollowingId(UUID followerId, UUID followingId);

    List<FollowJpaEntity> findByIdFollowingIdAndIsApproved(UUID followingId, boolean isApproved, Pageable pageable);

    List<FollowJpaEntity> findByIdFollowerIdAndIsApproved(UUID followerId, boolean isApproved, Pageable pageable);

    List<FollowJpaEntity> findByIdFollowingIdAndIsApprovedOrderByCreatedAtDesc(UUID followingId, boolean isApproved);

    @Transactional
    @Modifying
    @Query("DELETE FROM FollowJpaEntity f WHERE f.id.followerId = :followerId AND f.id.followingId = :followingId")
    void deleteByFollowerIdAndFollowingId(@Param("followerId") UUID followerId, @Param("followingId") UUID followingId);

    long countByIdFollowingIdAndIsApproved(UUID followingId, boolean isApproved);

    long countByIdFollowerIdAndIsApproved(UUID followerId, boolean isApproved);

    @Query("SELECT f FROM FollowJpaEntity f WHERE f.id.followerId = :followerId AND f.id.followingId IN :followingIds")
    List<FollowJpaEntity> findByIdFollowerIdAndIdFollowingIdIn(@Param("followerId") UUID followerId,
            @Param("followingIds") List<UUID> followingIds);

    @Query(value = """
            SELECT f.* FROM follows f
            WHERE f.following_id = :followingId
              AND f.is_approved = TRUE
              AND (:cursorTs IS NULL
                   OR (f.created_at, f.follower_id) < (:cursorTs::timestamptz, :cursorId::uuid))
            ORDER BY f.created_at DESC, f.follower_id DESC
            LIMIT :size
            """, nativeQuery = true)
    List<FollowJpaEntity> findFollowersKeysetBefore(
            @Param("followingId") UUID followingId,
            @Param("cursorTs") String cursorTs,
            @Param("cursorId") UUID cursorId,
            @Param("size") int size);

    @Query(value = """
            SELECT f.* FROM follows f
            WHERE f.follower_id = :followerId
              AND f.is_approved = TRUE
              AND (:cursorTs IS NULL
                   OR (f.created_at, f.following_id) < (:cursorTs::timestamptz, :cursorId::uuid))
            ORDER BY f.created_at DESC, f.following_id DESC
            LIMIT :size
            """, nativeQuery = true)
    List<FollowJpaEntity> findFollowingKeysetBefore(
            @Param("followerId") UUID followerId,
            @Param("cursorTs") String cursorTs,
            @Param("cursorId") UUID cursorId,
            @Param("size") int size);

}
