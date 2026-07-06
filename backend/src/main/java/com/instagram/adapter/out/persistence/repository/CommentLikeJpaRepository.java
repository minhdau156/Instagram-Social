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

    @Query("SELECT cl.id.commentId FROM CommentLikeJpaEntity cl " +
    " WHERE cl.id.userId = :userId AND cl.id.commentId IN :commentIds")
    Set<UUID> findByUserIdAndCommentIdIn(@Param("userId") UUID userId, @Param("commentIds") List<UUID> commentIds);

}