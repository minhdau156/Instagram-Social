package com.instagram.adapter.out.persistence;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.adapter.out.persistence.entity.SavePostId;
import com.instagram.adapter.out.persistence.entity.SavedPostJpaEntity;
import com.instagram.adapter.out.persistence.repository.SavedPostJpaRepository;
import com.instagram.domain.model.SavedPost;
import com.instagram.domain.port.out.SavedPostRepository;

@Component
public class SavedPostPersistenceAdapter implements SavedPostRepository {

    private final SavedPostJpaRepository savedPostJpaRepository;

    public SavedPostPersistenceAdapter(SavedPostJpaRepository savedPostJpaRepository) {
        this.savedPostJpaRepository = savedPostJpaRepository;
    }

    @Override
    @Transactional
    public SavedPost save(SavedPost savedPost) {
        SavedPostJpaEntity entity = toEntity(savedPost);
        SavedPostJpaEntity savedEntity = savedPostJpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    @Transactional
    public void delete(UUID postId, UUID userId) {
        savedPostJpaRepository.deleteByIdPostIdAndIdUserId(postId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByPostIdAndUserId(UUID postId, UUID userId) {
        return savedPostJpaRepository.existsByIdPostIdAndIdUserId(postId, userId);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SavedPost> findByUserId(UUID userId, Pageable pageable) {
        Page<SavedPostJpaEntity> page = savedPostJpaRepository.findByIdUserIdOrderBySavedAtDesc(userId, pageable);
        return page.map(this::toDomain);
    }

    private SavedPost toDomain(SavedPostJpaEntity entity) {
        return SavedPost.builder()
                .postId(entity.getId().getPostId())
                .userId(entity.getId().getUserId())
                .savedAt(entity.getSavedAt())
                .build();
    }

    private SavedPostJpaEntity toEntity(SavedPost savedPost) {
        return SavedPostJpaEntity.builder()
                .id(new SavePostId(savedPost.getPostId(), savedPost.getUserId()))
                .savedAt(savedPost.getSavedAt())
                .build();
    }

}
