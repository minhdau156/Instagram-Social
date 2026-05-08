package com.instagram.adapter.out.persistence;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.repository.FeedJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.domain.model.Hashtag;
import com.instagram.domain.model.Post;
import com.instagram.domain.port.out.FeedRepository;

@Component
public class FeedJpaQueryAdapter implements FeedRepository {

    private final FeedJpaRepository feedJpaRepository;
    private final PostJpaRepository postJpaRepository; // for toDomain mapping reuse

    public FeedJpaQueryAdapter(FeedJpaRepository feedJpaRepository,
            PostJpaRepository postJpaRepository) {
        this.feedJpaRepository = feedJpaRepository;
        this.postJpaRepository = postJpaRepository;
    }

    @Override
    public List<Post> getHomeFeed(UUID userId, UUID cursor, int limit) {
        return feedJpaRepository.findHomeFeed(userId, cursor, limit)
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Post> getExploreFeed(UUID userId, UUID cursor, int limit) {
        return feedJpaRepository.findExploreFeed(userId, cursor, limit)
                .stream()
                .map(this::toDomain)
                .toList();
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
