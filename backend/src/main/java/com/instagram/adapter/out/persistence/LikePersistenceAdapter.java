package com.instagram.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.CommentLikeId;
import com.instagram.adapter.out.persistence.entity.CommentLikeJpaEntity;
import com.instagram.adapter.out.persistence.entity.PostLikeId;
import com.instagram.adapter.out.persistence.entity.PostLikeJpaEntity;
import com.instagram.adapter.out.persistence.repository.CommentJpaRepository;
import com.instagram.adapter.out.persistence.repository.CommentLikeJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostLikeJpaRepository;
import com.instagram.domain.port.out.LikeRepository;

import jakarta.transaction.Transactional;

@Component
public class LikePersistenceAdapter implements LikeRepository {

    private final PostLikeJpaRepository postLikeJpaRepository;
    private final PostJpaRepository postJpaRepository;
    private final CommentLikeJpaRepository commentLikeJpaRepository;
    // private final CommentJpaRepository commentJpaRepository;

    public LikePersistenceAdapter(PostLikeJpaRepository postLikeJpaRepository,
            PostJpaRepository postJpaRepository,
            CommentLikeJpaRepository commentLikeJpaRepository) {
        this.postLikeJpaRepository = postLikeJpaRepository;
        this.postJpaRepository = postJpaRepository;
        this.commentLikeJpaRepository = commentLikeJpaRepository;
        // this.commentJpaRepository = commentJpaRepository;
    }

    @Override
    @Transactional
    public void likePost(UUID postId, UUID userId) {
        PostLikeId id = new PostLikeId(postId, userId);
        PostLikeJpaEntity postLikeJpaEntity = new PostLikeJpaEntity(id);
        postLikeJpaRepository.save(postLikeJpaEntity);
        postJpaRepository.incrementLikeCount(postId);
    }

    @Override
    @Transactional
    public void unlikePost(UUID postId, UUID userId) {
        postLikeJpaRepository.deleteByIdPostIdAndIdUserId(postId, userId);
        postJpaRepository.decrementLikeCount(postId);
    }

    @Override
    public boolean hasLikedPost(UUID postId, UUID userId) {
        return postLikeJpaRepository.existsByIdPostIdAndIdUserId(postId, userId);
    }

    @Override
    public List<UUID> findPostLikerIds(UUID postId, Pageable pageable) {
        return postLikeJpaRepository
                .findByIdPostIdOrderByCreatedAtDesc(postId, pageable)
                .stream()
                .map(e -> e.getId().getUserId())
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void likeComment(UUID commentId, UUID userId) {
        CommentLikeId id = new CommentLikeId(commentId, userId);
        CommentLikeJpaEntity commentLikeJpaEntity = new CommentLikeJpaEntity(id);
        commentLikeJpaRepository.save(commentLikeJpaEntity);
        // commentJpaRepository.incrementLikeCount(commentId);
    }

    @Override
    @Transactional
    public void unlikeComment(UUID commentId, UUID userId) {
        commentLikeJpaRepository.deleteByIdCommentIdAndIdUserId(commentId, userId);
        // commentJpaRepository.decrementLikeCount(commentId);
    }

    @Override
    public boolean hasLikedComment(UUID commentId, UUID userId) {
        return commentLikeJpaRepository.existsByIdCommentIdAndIdUserId(commentId, userId);
    }

}
