package com.instagram.domain.port.out;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.PostMedia;

public interface PostMediaRepository {

    List<PostMedia> saveAll(List<PostMedia> mediaList);

    List<PostMedia> findByPostId(UUID postId);

    List<PostMedia> findByPostIds(Collection<UUID> postIds);
}
