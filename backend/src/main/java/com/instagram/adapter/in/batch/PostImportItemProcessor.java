package com.instagram.adapter.in.batch;

import com.instagram.adapter.in.batch.dto.PostImportRow;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.PostStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;

import java.time.OffsetDateTime;
import java.util.UUID;

public class PostImportItemProcessor implements ItemProcessor<PostImportRow, Post> {

    private static final Logger log = LoggerFactory.getLogger(PostImportItemProcessor.class);
    private static final int MAX_CAPTION_LENGTH = 2200;

    private final UUID userId;

    public PostImportItemProcessor(UUID userId) {
        this.userId = userId;
    }

    @Override
    public Post process(@NonNull PostImportRow row) {
        if (row.caption() != null && row.caption().length() > MAX_CAPTION_LENGTH) {
            log.warn("Skipping row: caption exceeds {} chars", MAX_CAPTION_LENGTH);
            return null;
        }

        OffsetDateTime createdAt;
        try {
            createdAt = (row.createdAt() != null && !row.createdAt().isBlank())
                    ? OffsetDateTime.parse(row.createdAt())
                    : OffsetDateTime.now();
        } catch (Exception e) {
            log.warn("Skipping row: invalid createdAt '{}'", row.createdAt());
            return null;
        }

        return Post.builder()
                .userId(userId)
                .caption(row.caption())
                .location(row.location())
                .status(PostStatus.PUBLISHED)
                .likeCount(0)
                .commentCount(0)
                .saveCount(0)
                .shareCount(0)
                .createdAt(createdAt)
                .updatedAt(createdAt)
                .build();
    }
}
