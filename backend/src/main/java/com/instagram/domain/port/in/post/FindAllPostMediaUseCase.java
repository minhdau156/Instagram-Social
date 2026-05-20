package com.instagram.domain.port.in.post;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.instagram.domain.model.PostMedia;

public interface FindAllPostMediaUseCase {
    List<PostMedia> findAllByPostIds(Collection<UUID> postIds);
}
