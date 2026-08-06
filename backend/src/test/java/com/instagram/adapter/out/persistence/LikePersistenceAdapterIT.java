package com.instagram.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.instagram.adapter.out.persistence.entity.CommentJpaEntity;
import com.instagram.adapter.out.persistence.entity.CommentLikeId;
import com.instagram.adapter.out.persistence.entity.CommentLikeJpaEntity;
import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.entity.PostLikeId;
import com.instagram.adapter.out.persistence.entity.PostLikeJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.instagram.adapter.out.persistence.repository.CommentJpaRepository;
import com.instagram.adapter.out.persistence.repository.CommentLikeJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostLikeJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserJpaRepository;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.UserStatus;

public class LikePersistenceAdapterIT extends PostgresIntegrationTest {
    @Autowired
    PostLikeJpaRepository postLikeJpaRepository;

    @Autowired
    CommentLikeJpaRepository commentLikeJpaRepository;

    @Autowired
    PostJpaRepository postJpaRepository;

    @Autowired
    CommentJpaRepository commentJpaRepository;

    @Autowired
    UserJpaRepository userJpaRepository;

    LikePersistenceAdapter likePersistenceAdapter;

    @BeforeEach
    void setUp() {
        likePersistenceAdapter = new LikePersistenceAdapter(
                postLikeJpaRepository, postJpaRepository, commentLikeJpaRepository, commentJpaRepository);
    }

    // post_likes.user_id/post_id and comment_likes.user_id/comment_id are real
    // FKs — every test needs actually-persisted parent rows rather than bare
    // UUID.randomUUID() values.
    private UUID persistUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userJpaRepository.save(UserJpaEntity.builder()
                .username("like_user_" + suffix)
                .email("like_user_" + suffix + "@example.com")
                .fullName("Like User")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build()).getId();
    }

    private UUID persistPost(UUID userId) {
        return postJpaRepository.save(PostJpaEntity.builder()
                .userId(userId)
                .caption("Post for like test")
                .status(PostStatus.PUBLISHED)
                .build()).getId();
    }

    private UUID persistComment(UUID userId, UUID postId) {
        return commentJpaRepository.save(CommentJpaEntity.builder()
                .id(UUID.randomUUID())
                .postId(postId)
                .userId(userId)
                .content("Comment for like test")
                .build()).getId();
    }

    @Test
    void likePost_persistsLikeRecord_shouldSuccess() {
        UUID userId = persistUser();
        UUID postId = persistPost(persistUser());
        likePersistenceAdapter.likePost(postId, userId);
        assertTrue(postLikeJpaRepository.existsByIdPostIdAndIdUserId(postId, userId));
    }

    @Test
    void unlikePost_removesLikeRecord_shouldSuccess() {
        UUID userId = persistUser();
        UUID postId = persistPost(persistUser());
        likePersistenceAdapter.likePost(postId, userId);
        likePersistenceAdapter.unlikePost(postId, userId);
        assertFalse(postLikeJpaRepository.existsByIdPostIdAndIdUserId(postId, userId));
    }

    @Test
    void findPostLikerIds_returnsUserIdsSortedByCreatedAt_shouldSuccess() {
        UUID postId = persistPost(persistUser());
        UUID userId = persistUser();
        UUID userId2 = persistUser();
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
        UUID authorId = persistUser();
        UUID postId = persistPost(authorId);
        UUID commentId = persistComment(authorId, postId);
        UUID userId = persistUser();
        likePersistenceAdapter.likeComment(commentId, userId);
        assertTrue(commentLikeJpaRepository.existsByIdCommentIdAndIdUserId(commentId, userId));
    }

    @Test
    void unlikeComment_removesCommentLikeRecord_shouldSuccess() {
        UUID authorId = persistUser();
        UUID postId = persistPost(authorId);
        UUID commentId = persistComment(authorId, postId);
        UUID userId = persistUser();
        likePersistenceAdapter.likeComment(commentId, userId);
        likePersistenceAdapter.unlikeComment(commentId, userId);
        assertFalse(commentLikeJpaRepository.existsByIdCommentIdAndIdUserId(commentId, userId));
    }

    @Test
    void hasLikedPost_persistsLikeRecord_shouldReturnTrue() {
        UUID userId = persistUser();
        UUID postId = persistPost(persistUser());
        PostLikeId id = new PostLikeId(postId, userId);
        PostLikeJpaEntity postLikeJpaEntity = new PostLikeJpaEntity(id);
        postLikeJpaRepository.save(postLikeJpaEntity);

        boolean result = likePersistenceAdapter.hasLikedPost(postId, userId);
        assertTrue(result);

    }

    @Test
    void hasLikedComment_persistsCommentRecord_shouldReturnTrue() {
        UUID authorId = persistUser();
        UUID postId = persistPost(authorId);
        UUID commentId = persistComment(authorId, postId);
        UUID userId = persistUser();

        CommentLikeId id = new CommentLikeId(commentId, userId);
        CommentLikeJpaEntity entity = new CommentLikeJpaEntity(id);
        commentLikeJpaRepository.save(entity);

        boolean result = likePersistenceAdapter.hasLikedComment(commentId, userId);
        assertTrue(result);

    }

    @Test
    void findLikedPostIdsByUserIdAndPostIds_whenPersistSomeRecord_shouldReturnSetOfUUID () {
        UUID userId = persistUser();
        UUID postOwnerId = persistUser();
        UUID postId1 = persistPost(postOwnerId);
        UUID postId2 = persistPost(postOwnerId);

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
        UUID userId = persistUser();
        UUID authorId = persistUser();
        UUID postId = persistPost(authorId);
        UUID commentId = persistComment(authorId, postId);
        UUID commentId2 = persistComment(authorId, postId);
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
