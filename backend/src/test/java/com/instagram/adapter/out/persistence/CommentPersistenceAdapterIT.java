package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestPropertySource;

import com.instagram.adapter.out.persistence.entity.CommentJpaEntity;
import com.instagram.adapter.out.persistence.repository.CommentJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.domain.model.Comment;
import com.instagram.domain.model.CommentStatus;

@DataJpaTest
@Import(CommentPersistenceAdapter.class)
@TestPropertySource(properties = { "spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop" })
class CommentPersistenceAdapterIT {

        @Autowired
        private CommentPersistenceAdapter adapter;

        @Autowired
        private CommentJpaRepository commentJpaRepository;

        @Autowired
        private PostJpaRepository postJpaRepository;

        @BeforeEach
        void setUp() {
                commentJpaRepository.deleteAll();
        }

        @Test
        void save_persistsComment_withCorrectFields() {
                UUID postId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();

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
                UUID postId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();

                Comment topLevelActive = adapter.save(Comment.builder()
                                .id(UUID.randomUUID()).postId(postId).userId(userId).content("Top level")
                                .status(CommentStatus.ACTIVE).build());

                adapter.save(Comment.builder()
                                .id(UUID.randomUUID()).postId(postId).userId(userId).parentId(topLevelActive.getId())
                                .content("Reply").status(CommentStatus.ACTIVE).build());

                adapter.save(Comment.builder()
                                .id(UUID.randomUUID()).postId(postId).userId(userId).content("Deleted")
                                .status(CommentStatus.DELETED).build());

                Page<Comment> result = adapter.findByPostId(postId, PageRequest.of(0, 10));

                assertThat(result.getContent()).hasSize(1);
                assertThat(result.getContent().get(0).getId()).isEqualTo(topLevelActive.getId());
        }

        @Test
        void findByParentId_returnsActiveRepliesOnly() {
                UUID postId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();

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
                UUID postId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();

                Comment comment = adapter.save(Comment.builder()
                                .id(UUID.randomUUID()).postId(postId).userId(userId).content("Top level")
                                .status(CommentStatus.ACTIVE).replyCount(0).build());

                adapter.incrementReplyCount(comment.getId());

                CommentJpaEntity entity = commentJpaRepository.findById(comment.getId()).orElseThrow();
                assertThat(entity.getReplyCount()).isEqualTo(1);
        }

        @Test
        void save_softDeleted_commentHasDeletedStatus() {
                UUID postId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();

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
