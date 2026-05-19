package com.instagram.domain.port.out;

import java.util.List;

import org.springframework.data.domain.Pageable;

import com.instagram.domain.model.Hashtag;
import com.instagram.domain.model.Post;
import com.instagram.domain.model.User;

public interface SearchRepository {
    List<User> searchUsers(String query, Pageable pageable);

    List<Hashtag> searchHashtags(String query, Pageable pageable);

    List<Post> searchPosts(String query, Pageable pageable);

    List<Post> findPostsByHashtag(String hashtagName, Pageable pageable);

}
