package com.instagram.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.TestPropertySource;

import com.instagram.adapter.out.persistence.repository.CommentJpaRepository;
import com.instagram.adapter.out.persistence.repository.CommentLikeJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostLikeJpaRepository;
import com.instagram.infrastructure.config.JpaConfig;

@DataJpaTest
@Import(JpaConfig.class)
@TestPropertySource(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop" })
public class LikePersistenceAdapterIT {
    @Autowired
    PostLikeJpaRepository postLikeJpaRepository;

    @Autowired
    CommentLikeJpaRepository commentLikeJpaRepository;

    @Autowired
    PostJpaRepository postJpaRepository;

    LikePersistenceAdapter likePersistenceAdapter;

    @BeforeEach
    void setUp() {
        likePersistenceAdapter = new LikePersistenceAdapter(
                postLikeJpaRepository, postJpaRepository, commentLikeJpaRepository);
    }

    @Test
    void likePost_persistsLikeRecord_shouldSuccess() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        likePersistenceAdapter.likePost(postId, userId);
        assertTrue(postLikeJpaRepository.existsByIdPostIdAndIdUserId(postId, userId));
    }

    @Test
    void unlikePost_removesLikeRecord_shouldSuccess() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        likePersistenceAdapter.likePost(postId, userId);
        likePersistenceAdapter.unlikePost(postId, userId);
        assertFalse(postLikeJpaRepository.existsByIdPostIdAndIdUserId(postId, userId));
    }

    @Test
    void findPostLikerIds_returnsUserIdsSortedByCreatedAt_shouldSuccess() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        likePersistenceAdapter.likePost(postId, userId);
        likePersistenceAdapter.likePost(postId, userId2);
        List<UUID> likerIds = likePersistenceAdapter.findPostLikerIds(postId, Pageable.unpaged());
        assertTrue(likerIds.contains(userId));
        assertTrue(likerIds.contains(userId2));
        assertEquals(2, likerIds.size());
        assertEquals(userId2, likerIds.get(0));
        assertEquals(userId, likerIds.get(1));
    }

    @Test
    void likeComment_persistsCommentLikeRecord_shouldSuccess() {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        likePersistenceAdapter.likeComment(commentId, userId);
        assertTrue(commentLikeJpaRepository.existsByIdCommentIdAndIdUserId(commentId, userId));
    }

    @Test
    void unlikeComment_removesCommentLikeRecord_shouldSuccess() {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        likePersistenceAdapter.likeComment(commentId, userId);
        likePersistenceAdapter.unlikeComment(commentId, userId);
        assertFalse(commentLikeJpaRepository.existsByIdCommentIdAndIdUserId(commentId, userId));
    }

}
