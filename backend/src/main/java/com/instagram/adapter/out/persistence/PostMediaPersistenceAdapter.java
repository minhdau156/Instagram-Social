package com.instagram.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.entity.PostMediaJpaEntity;
import com.instagram.adapter.out.persistence.repository.PostMediaJpaRepository;
import com.instagram.domain.model.PostMedia;
import com.instagram.domain.port.out.PostMediaRepository;

@Component
public class PostMediaPersistenceAdapter implements PostMediaRepository {
    private final PostMediaJpaRepository jpaRepository;

    public PostMediaPersistenceAdapter(PostMediaJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public List<PostMedia> saveAll(List<PostMedia> mediaList) {
        return jpaRepository.saveAll(mediaList.stream().map(this::toEntity).toList())
                .stream().map(this::toDomain).toList();
    }

    @Override
    public List<PostMedia> findByPostId(UUID postId) {
        return jpaRepository.findByPostIdOrderBySortOrderAsc(postId).stream().map(this::toDomain).toList();
    }

    private PostMedia toDomain(PostMediaJpaEntity e) {
        return PostMedia.builder()
                .id(e.getId())
                .postId(e.getPost().getId())
                .mediaUrl(e.getMediaUrl())
                .mediaType(e.getMediaType())
                .thumbnailUrl(e.getThumbnailUrl())
                .width(e.getWidth())
                .height(e.getHeight())
                .duration(e.getDurationSecs())
                .fileSizeBytes(e.getFileSizeBytes())
                .sortOrder(e.getSortOrder().toString())
                .build();
    }

    private PostMediaJpaEntity toEntity(PostMedia media) {
        return PostMediaJpaEntity.builder()
                .id(media.getId())
                .post(PostJpaEntity.builder().id(media.getPostId()).build())
                .mediaUrl(media.getMediaUrl())
                .mediaType(media.getMediaType())
                .thumbnailUrl(media.getThumbnailUrl())
                .width(media.getWidth())
                .height(media.getHeight())
                .durationSecs(media.getDuration())
                .fileSizeBytes(media.getFileSizeBytes())
                .sortOrder(Short.parseShort(media.getSortOrder()))
                .build();
    }

    @Override
    public List<PostMedia> findByPostIds(Collection<UUID> postIds) {
        return jpaRepository.findByPostIdIn(postIds)
                .stream()
                .map(this::toDomain)
                .toList();
    }

}
