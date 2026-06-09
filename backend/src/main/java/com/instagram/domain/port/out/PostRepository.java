package com.instagram.domain.port.out;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.instagram.domain.model.Post;

public interface PostRepository {
    Post save(Post post);

    Optional<Post> findById(UUID id);

    Page<Post> findByUserId(UUID userId, Pageable pageable);

    void deleteById(UUID id);

    void incrementLikeCount(UUID postId);

    void decrementLikeCount(UUID postId);

    Stream<Post> streamByUserId(UUID userId);
}
