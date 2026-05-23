package com.instagram.adapter.out.persistence;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.repository.FeedJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.domain.model.Hashtag;
import com.instagram.domain.model.Post;
import com.instagram.domain.port.out.FeedRepository;
import com.instagram.infrastructure.util.BlockFilter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@Component
public class FeedJpaQueryAdapter implements FeedRepository {

    private final FeedJpaRepository feedJpaRepository;
    private final PostJpaRepository postJpaRepository; // for toDomain mapping reuse
    private final BlockFilter blockFilter;
    private final EntityManager em;

    public FeedJpaQueryAdapter(FeedJpaRepository feedJpaRepository,
            PostJpaRepository postJpaRepository,
            BlockFilter blockFilter,
            EntityManager em) {
        this.feedJpaRepository = feedJpaRepository;
        this.postJpaRepository = postJpaRepository;
        this.blockFilter = blockFilter;
        this.em = em;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Post> getHomeFeed(UUID userId, UUID cursor, int limit) {
        Set<UUID> excludedIds = blockFilter.getExcludedUserIds(userId);

        if (excludedIds.isEmpty()) {
            return feedJpaRepository.findHomeFeed(userId, cursor, limit)
                    .stream()
                    .map(this::toDomain)
                    .toList();
        }
        String sql = "SELECT p.* FROM posts p JOIN follows f ON f.following_id = p.user_id " +
                "WHERE f.follower_id = :userId " +
                "AND f.is_approved = true " +
                "AND (:cursor IS NULL OR p.id < :cursor) " +
                "AND p.deleted_at IS NULL " +
                "AND p.user_id NOT IN (:excludedIds) " +
                "ORDER BY p.created_at DESC " +
                "LIMIT :limit";
        Query nativeQuery = em.createNativeQuery(sql, PostJpaEntity.class);
        nativeQuery.setParameter("userId", userId);
        nativeQuery.setParameter("cursor", cursor);
        nativeQuery.setParameter("limit", limit);
        nativeQuery.setParameter("excludedIds", List.copyOf(excludedIds));
        List<PostJpaEntity> entities = nativeQuery.getResultList();
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<Post> getExploreFeed(UUID userId, UUID cursor, int limit) {
        Set<UUID> excludedIds = blockFilter.getExcludedUserIds(userId);

        if (excludedIds.isEmpty()) {
            return feedJpaRepository.findExploreFeed(userId, cursor, limit)
                    .stream()
                    .map(this::toDomain)
                    .toList();
        }
        String sql = "SELECT p.* FROM posts p " +
                "WHERE p.user_id NOT IN (" +
                "    SELECT f.following_id FROM follows f " +
                "    WHERE f.follower_id = :userId AND f.is_approved = true" +
                ") " +
                "AND p.user_id NOT IN (:excludedIds) " +
                "AND p.user_id <> :userId " +
                "AND p.deleted_at IS NULL " +
                "AND (:cursor IS NULL OR p.id < :cursor) " +
                "ORDER BY (p.like_count + p.comment_count) DESC, p.created_at DESC " +
                "LIMIT :limit";
        Query nativeQuery = em.createNativeQuery(sql, PostJpaEntity.class);
        nativeQuery.setParameter("userId", userId);
        nativeQuery.setParameter("cursor", cursor);
        nativeQuery.setParameter("limit", limit);
        nativeQuery.setParameter("excludedIds", List.copyOf(excludedIds));
        List<PostJpaEntity> entities = nativeQuery.getResultList();
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public List<Hashtag> getTrendingHashtags(int limit) {
        return feedJpaRepository.findTrendingHashtags(limit)
                .stream()
                .map(this::toHashtagDomain)
                .toList();
    }

    private Post toDomain(PostJpaEntity entity) {
        // Reuse the same mapping already in PostPersistenceAdapter
        // Copy the toDomain logic here, or extract a shared PostMapper utility
        // Do NOT call PostPersistenceAdapter directly — copy/adapt the mapping
        return Post.builder()
                .id(entity.getId())
                .userId(entity.getUser().getId())
                .caption(entity.getCaption())
                .location(entity.getLocation())
                .status(entity.getStatus())
                .likeCount(entity.getLikeCount())
                .commentCount(entity.getCommentCount())
                .saveCount(entity.getSaveCount())
                .shareCount(entity.getShareCount())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private Hashtag toHashtagDomain(Object[] row) {
        // row[0]=id, row[1]=name, row[2]=postCount, row[3]=createdAt
        return Hashtag.builder()
                .id((UUID) row[0])
                .name((String) row[1])
                .postCount(((Number) row[2]).intValue())
                .build();
    }
}
