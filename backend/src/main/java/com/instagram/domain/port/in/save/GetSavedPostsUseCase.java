package com.instagram.domain.port.in.save;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;

import com.instagram.domain.model.SavedPost;

public interface GetSavedPostsUseCase {
    Page<SavedPost> getSavedPosts(Query query);

    record Query(UUID userId, int page, int size) {
    }
}