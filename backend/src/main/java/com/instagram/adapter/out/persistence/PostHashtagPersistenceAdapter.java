package com.instagram.adapter.out.persistence;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.PostHashtagId;
import com.instagram.adapter.out.persistence.entity.PostHashtagJpaEntity;
import com.instagram.adapter.out.persistence.repository.PostHashtagJpaRepository;
import com.instagram.domain.port.out.PostHashtagRepository;

@Component
public class PostHashtagPersistenceAdapter implements PostHashtagRepository {
    private final PostHashtagJpaRepository postHashtagJpaRepository;

    public PostHashtagPersistenceAdapter(PostHashtagJpaRepository postHashtagJpaRepository) {
        this.postHashtagJpaRepository = postHashtagJpaRepository;
    }

    @Override
    public void save(UUID postId, UUID hashtagId) {
        PostHashtagId id = new PostHashtagId(postId, hashtagId);
        PostHashtagJpaEntity entity = new PostHashtagJpaEntity(id);
        postHashtagJpaRepository.save(entity);
    }

}
