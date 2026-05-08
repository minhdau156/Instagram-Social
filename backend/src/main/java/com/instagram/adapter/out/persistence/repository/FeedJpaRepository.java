package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.instagram.adapter.out.persistence.entity.PostJpaEntity;

public interface FeedJpaRepository extends JpaRepository<PostJpaEntity, UUID> {

    @Query(value = """
            SELECT p.* FROM posts p
            JOIN follows f ON f.following_id = p.user_id
            WHERE f.follower_id = :userId
              AND f.is_approved = true
              AND (:cursor IS NULL OR p.id < :cursor)
              AND p.deleted_at IS NULL
            ORDER BY p.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<PostJpaEntity> findHomeFeed(
            @Param("userId") UUID userId,
            @Param("cursor") UUID cursor,
            @Param("limit") int limit);

    @Query(value = """
            SELECT p.* FROM posts p
            WHERE p.user_id NOT IN (
                SELECT f.following_id FROM follows f
                WHERE f.follower_id = :userId AND f.is_approved = true
            )
              AND p.user_id <> :userId
              AND p.deleted_at IS NULL
              AND (:cursor IS NULL OR p.id < :cursor)
            ORDER BY (p.like_count + p.comment_count) DESC, p.created_at DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<PostJpaEntity> findExploreFeed(
            @Param("userId") UUID userId,
            @Param("cursor") UUID cursor,
            @Param("limit") int limit);

    @Query(value = """
            SELECT h.id, h.name, h.post_count, h.created_at,
                   hs.weekly_count, hs.updated_at
            FROM hashtags h
            JOIN hashtag_stats hs ON hs.hashtag_id = h.id
            ORDER BY hs.weekly_count DESC
            LIMIT :limit
            """, nativeQuery = true)
    List<Object[]> findTrendingHashtags(@Param("limit") int limit);
}
