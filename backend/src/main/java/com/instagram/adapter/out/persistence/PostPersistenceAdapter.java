package com.instagram.adapter.out.persistence;

import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.port.out.PostRepository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Persistence adapter — implements the {@link PostRepository} output port
 * using Spring Data JPA.
 *
 * <p>
 * Responsibilities:
 * <ul>
 * <li>Map domain {@link Post} ↔ {@link PostJpaEntity}</li>
 * <li>Delegate persistence operations to {@link PostJpaRepository}</li>
 * </ul>
 * </p>
 */
@Component
public class PostPersistenceAdapter implements PostRepository {

    private final PostJpaRepository jpaRepository;

    public PostPersistenceAdapter(PostJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    // ── PostRepository (output port) ─────────────────────────────────────── //

    @Override
    public Post save(Post post) {
        PostJpaEntity entity = toEntity(post);
        PostJpaEntity saved = jpaRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Post> findById(UUID id) {
        return jpaRepository.findByIdAndStatusNot(id, PostStatus.DELETED)
                .map(this::toDomain);
    }

    @Override
    public Page<Post> findByUserId(UUID userId, Pageable pageable) {
        return jpaRepository.findByUserIdAndStatusNot(userId, PostStatus.DELETED, pageable)
                .map(this::toDomain); // To be implemented in Task 2.9
    }

    @Override
    public void deleteById(UUID id) {
        jpaRepository.findByIdAndStatusNot(id, PostStatus.DELETED)
                .ifPresent(post -> {
                    post.setStatus(PostStatus.DELETED);
                    jpaRepository.save(post);
                });
    }

    @Override
    public void incrementLikeCount(UUID postId) {
        jpaRepository.incrementLikeCount(postId);
    }

    @Override
    public void decrementLikeCount(UUID postId) {
        jpaRepository.decrementLikeCount(postId);
    }

    @Override
    @Transactional(readOnly = true)
    public Stream<Post> streamByUserId(UUID userId) {
        return jpaRepository.streamByUserId(userId)
                .map(this::toDomain);
    }

    // ── Mapping ──────────────────────────────────────────────────────────── //

    private PostJpaEntity toEntity(Post post) {
        PostJpaEntity jpaEntity = PostJpaEntity.builder()
                .id(post.getId()) // null → Hibernate generates UUID
                .userId(post.getUserId())
                .caption(post.getCaption())
                .location(post.getLocation())
                .status(post.getStatus() != null ? post.getStatus() : PostStatus.PUBLISHED)
                .viewCount(post.getViewCount())
                .likeCount(post.getLikeCount())
                .commentCount(post.getCommentCount())
                .saveCount(post.getSaveCount())
                .shareCount(post.getShareCount())
                .deletedAt(post.getDeletedAt())
                .build();
        jpaEntity.setCreatedAt(post.getCreatedAt());
        jpaEntity.setUpdatedAt(post.getUpdatedAt());
        return jpaEntity;
    }

    private Post toDomain(PostJpaEntity e) {
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
}
