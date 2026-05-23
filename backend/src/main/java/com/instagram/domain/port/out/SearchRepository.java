package com.instagram.domain.port.out;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.instagram.domain.model.Hashtag;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.User;

public interface SearchRepository {
    List<User> searchUsers(UUID currentUserId, String query, Pageable pageable);

    List<Hashtag> searchHashtags(String query, Pageable pageable);

    List<Post> searchPosts(UUID currentUserId, String query, Pageable pageable);

    List<Post> findPostsByHashtag(UUID currentUserId, String hashtagName, Pageable pageable);

}
