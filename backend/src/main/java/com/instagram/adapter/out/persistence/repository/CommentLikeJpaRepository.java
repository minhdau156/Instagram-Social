package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.instagram.adapter.out.persistence.entity.CommentLikeJpaEntity;
import com.instagram.adapter.out.persistence.entity.CommentLikeId;

public interface CommentLikeJpaRepository extends JpaRepository<CommentLikeJpaEntity, CommentLikeId> {

    boolean existsByIdCommentIdAndIdUserId(UUID commentId, UUID userId);

    void deleteByIdCommentIdAndIdUserId(UUID commentId, UUID userId);

    @Query(value = "SELECT cl.comment_id from comment_likes cl where cl.user_id = :userId and cl.comment_id in :commentIds", nativeQuery = true)
    Set<UUID> findByUserIdAndCommentIdIn(@Param("userId") UUID userId, @Param("commentIds") List<UUID> commentIds);

}