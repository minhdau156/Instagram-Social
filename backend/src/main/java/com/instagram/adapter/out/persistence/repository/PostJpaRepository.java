package com.instagram.adapter.out.persistence.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.domain.model.PostStatus;

import jakarta.persistence.QueryHint;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Spring Data JPA repository for {@link PostJpaEntity}.
 *
 * <p>
 * Internal to the persistence adapter — never referenced directly from
 * the application or domain layers.
 * </p>
 */
public interface PostJpaRepository extends JpaRepository<PostJpaEntity, UUID> {
    // Feed: user's non-deleted published posts, newest first
    Page<PostJpaEntity> findByUserIdAndStatusNot(
            UUID userId, PostStatus status, Pageable pageable);

    // Single post lookup that skips soft-deleted
    Optional<PostJpaEntity> findByIdAndStatusNot(UUID id, PostStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE PostJpaEntity p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
    void incrementLikeCount(@Param("postId") UUID postId);

    @Modifying
    @Transactional
    @Query("UPDATE PostJpaEntity p SET p.likeCount = GREATEST(p.likeCount - 1, 0) WHERE p.id = :postId")
    void decrementLikeCount(@Param("postId") UUID postId);

    @Modifying
    @Transactional
    @Query("UPDATE PostJpaEntity p SET p.commentCount = p.commentCount + 1 WHERE p.id = :postId")
    void incrementCommentCount(@Param("postId") UUID postId);

    @Modifying
    @Transactional
    @Query("UPDATE PostJpaEntity p SET p.commentCount = GREATEST(p.commentCount - 1, 0) WHERE p.id = :postId")
    void decrementCommentCount(@Param("postId") UUID postId);

    @Query("""
            SELECT p FROM PostJpaEntity p
            WHERE p.userId = :userId
              AND p.deletedAt IS NULL
            ORDER BY p.createdAt ASC
            """)
    @QueryHints(value = {
            @QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "50")
    })
    Stream<PostJpaEntity> streamByUserId(@Param("userId") UUID userId);
}
