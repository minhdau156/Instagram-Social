package com.instagram.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.instagram.adapter.out.persistence.entity.CommentLikeId;
import com.instagram.adapter.out.persistence.entity.CommentLikeJpaEntity;
import com.instagram.adapter.out.persistence.entity.PostLikeId;
import com.instagram.adapter.out.persistence.entity.PostLikeJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
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

    @Autowired
    CommentJpaRepository commentJpaRepository;

    LikePersistenceAdapter likePersistenceAdapter;

    @BeforeEach
    void setUp() {
        likePersistenceAdapter = new LikePersistenceAdapter(
                postLikeJpaRepository, postJpaRepository, commentLikeJpaRepository, commentJpaRepository);
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
        Page<UUID> likerIds = likePersistenceAdapter.findPostLikerIds(postId, Pageable.unpaged());
        assertTrue(likerIds.getContent().contains(userId));
        assertTrue(likerIds.getContent().contains(userId2));
        assertEquals(2, likerIds.getContent().size());
        assertEquals(userId2, likerIds.getContent().get(0));
        assertEquals(userId, likerIds.getContent().get(1));
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

    @Test
    void hasLikedPost_persistsLikeRecord_shouldReturnTrue() {
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PostLikeId id = new PostLikeId(postId, userId);
        PostLikeJpaEntity postLikeJpaEntity = new PostLikeJpaEntity(id);
        postLikeJpaRepository.save(postLikeJpaEntity);

        boolean result = likePersistenceAdapter.hasLikedPost(postId, userId);
        assertTrue(result);

    }

    @Test
    void hasLikedComment_persistsCommentRecord_shouldReturnTrue() {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CommentLikeId id = new CommentLikeId(commentId, userId);
        CommentLikeJpaEntity entity = new CommentLikeJpaEntity(id);
        commentLikeJpaRepository.save(entity);

        boolean result = likePersistenceAdapter.hasLikedComment(commentId, userId);
        assertTrue(result);

    }

    @Test
    void findLikedPostIdsByUserIdAndPostIds_whenPersistSomeRecord_shouldReturnSetOfUUID () {
        UUID userId = UUID.randomUUID();
        UUID postId1 = UUID.randomUUID();
        UUID postId2 = UUID.randomUUID();

        PostLikeId id1 = new PostLikeId(postId1, userId);
        PostLikeId id2 = new PostLikeId(postId2, userId);
        PostLikeJpaEntity postLikeJpaEntity1 = new PostLikeJpaEntity(id1);
        PostLikeJpaEntity postLikeJpaEntity2 = new PostLikeJpaEntity(id2);
        postLikeJpaRepository.save(postLikeJpaEntity1);
        postLikeJpaRepository.save(postLikeJpaEntity2);

        Set<UUID> result = likePersistenceAdapter.findLikedPostIdsByUserIdAndPostIds(userId, List.of(postId1, postId2));

        assertEquals(2, result.size());
    }

    @Test
    void findLikedCommentIdsByUserIdAndCommentIds_whenPersistsSomneRecord_shouldReturnSetOfUUID () {
        UUID userId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID commentId2 = UUID.randomUUID();
        CommentLikeId id1 = new CommentLikeId(commentId, userId);
        CommentLikeJpaEntity commentLikeJpaEntity1 = new CommentLikeJpaEntity(id1);
        CommentLikeId id2 = new CommentLikeId(commentId2, userId);
        CommentLikeJpaEntity commentLikeJpaEntity2 = new CommentLikeJpaEntity(id2);

        commentLikeJpaRepository.save(commentLikeJpaEntity1);
        commentLikeJpaRepository.save(commentLikeJpaEntity2);

        Set<UUID> result = likePersistenceAdapter.findLikedCommentIdsByUserIdAndCommentIds(userId, List.of(commentId, commentId2));

        assertEquals(2, result.size());
    }

}
