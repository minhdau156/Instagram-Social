package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import com.instagram.adapter.out.persistence.entity.CommentJpaEntity;
import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.repository.CommentJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserJpaRepository;
import com.instagram.domain.model.Comment;
import com.instagram.domain.model.CommentStatus;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.UserStatus;

@Import(CommentPersistenceAdapter.class)
class CommentPersistenceAdapterIT extends PostgresIntegrationTest {

        @Autowired
        private CommentPersistenceAdapter adapter;

        @Autowired
        private CommentJpaRepository commentJpaRepository;

        @Autowired
        private PostJpaRepository postJpaRepository;

        @Autowired
        private UserJpaRepository userJpaRepository;

        @BeforeEach
        void setUp() {
                commentJpaRepository.deleteAll();
        }

        // comments.post_id / comments.user_id are real FKs — every test needs an
        // actually-persisted parent row rather than a bare UUID.randomUUID().
        private UUID persistUser() {
                String suffix = UUID.randomUUID().toString().substring(0, 8);
                return userJpaRepository.save(UserJpaEntity.builder()
                                .username("commenter_" + suffix)
                                .email("commenter_" + suffix + "@example.com")
                                .fullName("Commenter")
                                .status(UserStatus.ACTIVE)
                                .privacyLevel(PrivacyLevel.PUBLIC)
                                .isVerified(false)
                                .build()).getId();
        }

        private UUID persistPost(UUID userId) {
                return postJpaRepository.save(PostJpaEntity.builder()
                                .userId(userId)
                                .caption("Post for comment test")
                                .status(PostStatus.PUBLISHED)
                                .build()).getId();
        }

        @Test
        void save_persistsComment_withCorrectFields() {
                UUID userId = persistUser();
                UUID postId = persistPost(userId);

                Comment comment = Comment.builder()
                                .id(UUID.randomUUID())
                                .postId(postId)
                                .userId(userId)
                                .content("Test comment")
                                .status(CommentStatus.ACTIVE)
                                .replyCount(0)
                                .likeCount(0)
                                .build();

                Comment saved = adapter.save(comment);

                assertThat(saved.getId()).isNotNull();
                assertThat(saved.getContent()).isEqualTo("Test comment");
                assertThat(saved.getPostId()).isEqualTo(postId);
                assertThat(saved.getUserId()).isEqualTo(userId);
                assertThat(saved.getStatus()).isEqualTo(CommentStatus.ACTIVE);

                CommentJpaEntity entity = commentJpaRepository.findById(saved.getId()).orElseThrow();
                assertThat(entity.getContent()).isEqualTo("Test comment");
                assertThat(entity.getPostId()).isEqualTo(postId);
                assertThat(entity.getUserId()).isEqualTo(userId);
                assertThat(entity.isDeleted()).isFalse();
        }

        @Test
        void findByPostId_returnsOnlyTopLevelActiveComments() {
                UUID userId = persistUser();
                UUID postId = persistPost(userId);

                Comment topLevelActive = adapter.save(Comment.builder()
                                .id(UUID.randomUUID()).postId(postId).userId(userId).content("Top level")
                                .status(CommentStatus.ACTIVE).build());

                adapter.save(Comment.builder()
                                .id(UUID.randomUUID()).postId(postId).userId(userId).parentId(topLevelActive.getId())
                                .content("Reply").status(CommentStatus.ACTIVE).build());

                adapter.save(Comment.builder()
                                .id(UUID.randomUUID()).postId(postId).userId(userId).content("Deleted")
                                .status(CommentStatus.DELETED).build());

                List<Comment> result = adapter.findByPostId(postId, null, null, 10);

                assertThat(result).hasSize(1);
                assertThat(result.get(0).getId()).isEqualTo(topLevelActive.getId());
        }

        @Test
        void findByParentId_returnsActiveRepliesOnly() {
                UUID userId = persistUser();
                UUID postId = persistPost(userId);

                Comment topLevel = adapter.save(Comment.builder()
                                .id(UUID.randomUUID()).postId(postId).userId(userId).content("Top level")
                                .status(CommentStatus.ACTIVE).build());

                Comment reply1 = adapter.save(Comment.builder()
                                .id(UUID.randomUUID()).postId(postId).userId(userId).parentId(topLevel.getId())
                                .content("Reply 1").status(CommentStatus.ACTIVE).build());

                Comment reply2 = adapter.save(Comment.builder()
                                .id(UUID.randomUUID()).postId(postId).userId(userId).parentId(topLevel.getId())
                                .content("Reply 2").status(CommentStatus.ACTIVE).build());

                adapter.save(Comment.builder()
                                .id(UUID.randomUUID()).postId(postId).userId(userId).parentId(topLevel.getId())
                                .content("Deleted Reply").status(CommentStatus.DELETED).build());

                Page<Comment> result = adapter.findByParentId(topLevel.getId(), PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(2);
                assertThat(result.getContent()).extracting("id").containsExactlyInAnyOrder(reply1.getId(),
                                reply2.getId());
        }

        @Test
        void incrementReplyCount_updatesCounterCorrectly() {
                UUID userId = persistUser();
                UUID postId = persistPost(userId);

                Comment comment = adapter.save(Comment.builder()
                                .id(UUID.randomUUID()).postId(postId).userId(userId).content("Top level")
                                .status(CommentStatus.ACTIVE).replyCount(0).build());

                adapter.incrementReplyCount(comment.getId());

                CommentJpaEntity entity = commentJpaRepository.findById(comment.getId()).orElseThrow();
                assertThat(entity.getReplyCount()).isEqualTo(1);
        }

        @Test
        void save_softDeleted_commentHasDeletedStatus() {
                UUID userId = persistUser();
                UUID postId = persistPost(userId);

                Comment comment = adapter.save(Comment.builder()
                                .id(UUID.randomUUID()).postId(postId).userId(userId).content("Top level")
                                .status(CommentStatus.ACTIVE).build());

                Comment deletedComment = Comment.builder()
                                .id(comment.getId())
                                .postId(comment.getPostId())
                                .userId(comment.getUserId())
                                .content(comment.getContent())
                                .status(CommentStatus.DELETED)
                                .build();

                adapter.save(deletedComment);

                Comment retrieved = adapter.findById(comment.getId()).orElseThrow();
                assertThat(retrieved.getStatus()).isEqualTo(CommentStatus.DELETED);

                CommentJpaEntity entity = commentJpaRepository.findById(comment.getId()).orElseThrow();
                assertThat(entity.isDeleted()).isTrue();
        }
}
