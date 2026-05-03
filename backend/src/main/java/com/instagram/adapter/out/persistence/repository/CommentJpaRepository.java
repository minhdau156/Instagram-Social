package com.instagram.adapter.out.persistence.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.adapter.out.persistence.entity.CommentJpaEntity;

public interface CommentJpaRepository extends JpaRepository<CommentJpaEntity, UUID> {

    @Query("SELECT c FROM CommentJpaEntity c WHERE c.postId = :postId AND c.parentId IS NULL AND c.isDeleted = false ORDER BY c.createdAt ASC")
    Page<CommentJpaEntity> findTopLevelByPostId(@Param("postId") UUID postId, Pageable pageable);

    @Query("SELECT c FROM CommentJpaEntity c WHERE c.parent.id = :parentId AND c.isDeleted = false ORDER BY c.createdAt ASC")
    Page<CommentJpaEntity> findRepliesByParentId(@Param("parentId") UUID parentId, Pageable pageable);

    @Modifying
    @Query("UPDATE CommentJpaEntity c SET c.replyCount = c.replyCount + 1 WHERE c.id = :id")
    void incrementReplyCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE CommentJpaEntity c SET c.replyCount = GREATEST(c.replyCount - 1, 0) WHERE c.id = :id")
    void decrementReplyCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE CommentJpaEntity c SET c.likeCount = c.likeCount + 1 WHERE c.id = :id")
    void incrementLikeCount(@Param("id") UUID id);

    @Modifying
    @Query("UPDATE CommentJpaEntity c SET c.likeCount = GREATEST(c.likeCount - 1, 0) WHERE c.id = :id")
    void decrementLikeCount(@Param("id") UUID id);

}
