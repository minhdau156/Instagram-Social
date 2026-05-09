package com.instagram.application.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.instagram.adapter.out.persistence.repository.PostHashtagJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserInterestJpaRepository;
import com.instagram.domain.port.out.UserInterestPort;

@Service
public class UserInterestService implements UserInterestPort {

    private final UserInterestJpaRepository repository;
    private final PostHashtagJpaRepository postHashtagRepository; // to resolve hashtag ids from a post

    public UserInterestService(UserInterestJpaRepository repository,
            PostHashtagJpaRepository postHashtagRepository) {
        this.repository = repository;
        this.postHashtagRepository = postHashtagRepository;
    }

    @Async("interestExecutor")
    public void recordLike(UUID userId, UUID postId) {
        // find all hashtag ids for this post and upsert score += 2.0
        postHashtagRepository.findHashtagIdsByPostId(postId)
                .forEach(hashtagId -> repository.upsertScore(userId, hashtagId, new BigDecimal("2.0")));
    }

    @Async("interestExecutor")
    public void recordComment(UUID userId, UUID postId) {
        // comment signals are weighted slightly lower
        postHashtagRepository.findHashtagIdsByPostId(postId)
                .forEach(hashtagId -> repository.upsertScore(userId, hashtagId, new BigDecimal("1.0")));
    }
}
