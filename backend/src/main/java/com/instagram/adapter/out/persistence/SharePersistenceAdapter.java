package com.instagram.adapter.out.persistence;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.PostShareJpaEntity;
import com.instagram.adapter.out.persistence.repository.PostShareJpaRepository;
import com.instagram.domain.model.PostShare;
import com.instagram.domain.model.ShareType;
import com.instagram.domain.port.out.ShareRepository;

@Component
public class SharePersistenceAdapter implements ShareRepository {
    private final PostShareJpaRepository postShareJpaRepository;

    public SharePersistenceAdapter(PostShareJpaRepository postShareJpaRepository) {
        this.postShareJpaRepository = postShareJpaRepository;
    }

    @Override
    public PostShare save(PostShare share) {
        PostShareJpaEntity entity = toEntity(share);
        PostShareJpaEntity savedEntity = postShareJpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public List<PostShare> findByPostId(UUID postId) {
        return postShareJpaRepository.findByPostId(postId).stream()
                .map(this::toDomain)
                .collect(Collectors.toList());
    }

    private PostShareJpaEntity toEntity(PostShare share) {
        return PostShareJpaEntity.builder()
                .id(share.getId())
                .postId(share.getPostId())
                .sharerId(share.getSharerId())
                .recipientId(share.getRecipientId())
                .build();
    }

    private PostShare toDomain(PostShareJpaEntity entity) {
        return PostShare.builder()
                .id(entity.getId())
                .postId(entity.getPostId())
                .sharerId(entity.getSharerId())
                .recipientId(entity.getRecipientId())
                .shareType(entity.getRecipientId() == null ? ShareType.LINK : ShareType.DM)
                .createdAt(entity.getCreatedAt())
                .build();
    }

}
