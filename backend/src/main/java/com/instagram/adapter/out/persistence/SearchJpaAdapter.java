package com.instagram.adapter.out.persistence;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.HashtagJpaEntity;
import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.domain.model.Hashtag;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.User;
import com.instagram.domain.port.out.SearchRepository;
import com.instagram.infrastructure.util.BlockFilter;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

@Component
public class SearchJpaAdapter implements SearchRepository {

        private final EntityManager em;
        private final BlockFilter blockFilter;
        private static final int FTS_MIN_LENGTH = 3;

        public SearchJpaAdapter(EntityManager em, BlockFilter blockFilter) {
                this.em = em;
                this.blockFilter = blockFilter;
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<User> searchUsers(UUID currentUserId, String query, Pageable pageable) {
                if (query == null || query.isBlank())
                        return Collections.emptyList();
                Set<UUID> excludedIds = blockFilter.getExcludedUserIds(currentUserId);
                String blockClause = excludedIds.isEmpty() ? "" : " AND id NOT IN (:excludedIds)";
                String sql = query.length() < FTS_MIN_LENGTH
                                ? "SELECT * FROM users WHERE (username ILIKE :pattern OR full_name ILIKE :pattern) AND deleted_at IS NULL"
                                                + blockClause
                                                + " ORDER BY follower_count DESC LIMIT :limit OFFSET :offset"
                                : "SELECT * FROM users WHERE search_tsv @@ plainto_tsquery('simple', :query) AND deleted_at IS NULL"
                                                + blockClause
                                                + " ORDER BY follower_count DESC LIMIT :limit OFFSET :offset";
                Query nativeQuery = em.createNativeQuery(sql, UserJpaEntity.class);
                if (query.length() < FTS_MIN_LENGTH) {
                        nativeQuery.setParameter("pattern", "%" + query + "%");
                } else {
                        nativeQuery.setParameter("query", query);
                }
                if (!excludedIds.isEmpty()) {
                        nativeQuery.setParameter("excludedIds", List.copyOf(excludedIds));
                }
                nativeQuery.setParameter("limit", pageable.getPageSize());
                nativeQuery.setParameter("offset", pageable.getOffset());
                List<UserJpaEntity> entities = nativeQuery.getResultList();
                return entities.stream()
                                .map(this::toUserDomain)
                                .collect(Collectors.toList());
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<Hashtag> searchHashtags(String query, Pageable pageable) {
                if (query == null || query.isBlank())
                        return Collections.emptyList();
                Query nativeQuery = em.createNativeQuery(
                                "SELECT * FROM hashtags WHERE name ILIKE :pattern ORDER BY post_count DESC LIMIT :limit OFFSET :offset",
                                HashtagJpaEntity.class);
                nativeQuery.setParameter("pattern", query + "%");
                nativeQuery.setParameter("limit", pageable.getPageSize());
                nativeQuery.setParameter("offset", pageable.getOffset());
                List<HashtagJpaEntity> entities = nativeQuery.getResultList();
                return entities.stream()
                                .map(this::toHashtagDomain)
                                .collect(Collectors.toList());
        }

        @SuppressWarnings("unchecked")
        @Override
        public List<Post> searchPosts(UUID currentUserId, String query, Pageable pageable) {
                if (query == null || query.isBlank())
                        return Collections.emptyList();
                Set<UUID> excludedIds = blockFilter.getExcludedUserIds(currentUserId);
                String blockClause = excludedIds.isEmpty() ? "" : " AND p.user_id NOT IN (:excludedIds)";
                String sql = query.length() < FTS_MIN_LENGTH
                                ? "SELECT p.* FROM posts p WHERE p.caption ILIKE :pattern AND p.deleted_at IS NULL"
                                                + blockClause
                                                + " ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset"
                                : "SELECT p.* FROM posts p WHERE p.caption_tsv @@ plainto_tsquery('english', :query) AND p.deleted_at IS NULL"
                                                + blockClause
                                                + " ORDER BY ts_rank(p.caption_tsv, plainto_tsquery('english', :query)) DESC, p.created_at DESC LIMIT :limit OFFSET :offset";
                Query nativeQuery = em.createNativeQuery(sql, PostJpaEntity.class);
                if (query.length() < FTS_MIN_LENGTH) {
                        nativeQuery.setParameter("pattern", "%" + query + "%");
                } else {
                        nativeQuery.setParameter("query", query);
                }
                if (!excludedIds.isEmpty()) {
                        nativeQuery.setParameter("excludedIds", List.copyOf(excludedIds));
                }
                nativeQuery.setParameter("limit", pageable.getPageSize());
                nativeQuery.setParameter("offset", pageable.getOffset());
                List<PostJpaEntity> entities = nativeQuery.getResultList();
                return entities.stream()
                                .map(this::toPostDomain)
                                .collect(Collectors.toList());
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<Post> findPostsByHashtag(UUID currentUserId, String hashtagName, Pageable pageable) {
                if (hashtagName == null || hashtagName.isBlank())
                        return Collections.emptyList();
                Set<UUID> excludedIds = blockFilter.getExcludedUserIds(currentUserId);
                String blockClause = excludedIds.isEmpty() ? "" : " AND p.user_id NOT IN (:excludedIds)";
                String sql = "SELECT p.* FROM posts p JOIN post_hashtags ph ON ph.post_id = p.id JOIN hashtags h ON h.id = ph.hashtag_id WHERE LOWER(h.name) = LOWER(:name) AND p.deleted_at IS NULL"
                                + blockClause + " ORDER BY p.created_at DESC LIMIT :limit OFFSET :offset";
                Query nativeQuery = em.createNativeQuery(sql, PostJpaEntity.class);
                nativeQuery.setParameter("name", hashtagName);
                if (!excludedIds.isEmpty()) {
                        nativeQuery.setParameter("excludedIds", List.copyOf(excludedIds));
                }
                nativeQuery.setParameter("limit", pageable.getPageSize());
                nativeQuery.setParameter("offset", pageable.getOffset());
                List<PostJpaEntity> entities = nativeQuery.getResultList();
                return entities.stream()
                                .map(this::toPostDomain)
                                .collect(Collectors.toList());
        }

        private Post toPostDomain(PostJpaEntity e) {
                return Post.builder()
                                .id(e.getId())
                                .userId(e.getUserId())
                                .caption(e.getCaption())
                                .location(e.getLocation())
                                .status(e.getStatus())
                                .viewCount(e.getViewCount())
                                .likeCount(e.getLikeCount())
                                .commentCount(e.getCommentCount())
                                .saveCount(e.getSaveCount())
                                .shareCount(e.getShareCount())
                                .createdAt(e.getCreatedAt())
                                .updatedAt(e.getUpdatedAt())
                                .deletedAt(e.getDeletedAt())
                                .build();
        }

        private User toUserDomain(UserJpaEntity entity) {
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

        private Hashtag toHashtagDomain(HashtagJpaEntity e) {
                return Hashtag.builder()
                                .id(e.getId())
                                .name(e.getName())
                                .postCount(e.getPostCount())
                                .build();
        }

}
