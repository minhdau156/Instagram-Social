package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.instagram.adapter.out.persistence.entity.PostJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.repository.PostJpaRepository;
import com.instagram.adapter.out.persistence.repository.PostShareJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserJpaRepository;
import com.instagram.domain.model.PostShare;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.ShareType;
import com.instagram.domain.model.UserStatus;

public class SharePersistenceAdapterIT extends PostgresIntegrationTest {

    @Autowired
    private PostShareJpaRepository postShareJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    @Autowired
    private PostJpaRepository postJpaRepository;

    private SharePersistenceAdapter adapter;

    private UUID postId;
    private UUID sharerId;

    @BeforeEach
    void setUp() {
        adapter = new SharePersistenceAdapter(postShareJpaRepository);

        UserJpaEntity user = userJpaRepository.save(UserJpaEntity.builder()
                .username("sharer")
                .email("sharer@example.com")
                .fullName("Test Sharer")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build());
        sharerId = user.getId();

        PostJpaEntity post = postJpaRepository.save(PostJpaEntity.builder()
                .userId(sharerId)
                .status(PostStatus.PUBLISHED)
                .build());
        postId = post.getId();
    }

    @Test
    void save_dmShare_persistsWithRecipientAndDmType() {
        // given
        // post_shares.shared_to_id is nullable but, when set, has a real FK to
        // users.id — a bare UUID.randomUUID() would violate that constraint.
        UUID recipientId = userJpaRepository.save(UserJpaEntity.builder()
                .username("recipient")
                .email("recipient@example.com")
                .fullName("Test Recipient")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build()).getId();
        PostShare share = PostShare.of(postId, sharerId, recipientId, ShareType.DM);

        // when
        PostShare saved = adapter.save(share);

        // then
        assertNotNull(saved.getId());
        assertEquals(postId, saved.getPostId());
        assertEquals(sharerId, saved.getSharerId());
        assertEquals(recipientId, saved.getRecipientId());
        assertEquals(ShareType.DM, saved.getShareType());
    }

    @Test
    void save_linkShare_persistsWithNullRecipientAndLinkType() {
        // given
        PostShare share = PostShare.of(postId, sharerId, null, ShareType.LINK);

        // when
        PostShare saved = adapter.save(share);

        // then
        assertNotNull(saved.getId());
        assertEquals(postId, saved.getPostId());
        assertEquals(sharerId, saved.getSharerId());
        assertNull(saved.getRecipientId());
        assertEquals(ShareType.LINK, saved.getShareType());
    }

    @Test
    void findByPostId_returnsSavedShares() {
        // given
        PostShare share1 = PostShare.of(postId, sharerId, null, ShareType.LINK);
        PostShare share2 = PostShare.of(postId, sharerId, null, ShareType.LINK);
        adapter.save(share1);
        adapter.save(share2);

        // when
        List<PostShare> result = adapter.findByPostId(postId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).allMatch(s -> s.getPostId().equals(postId));
    }

    @Test
    void findByPostId_whenNoShares_returnsEmpty() {
        // given / when
        List<PostShare> result = adapter.findByPostId(UUID.randomUUID());

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void findByPostId_doesNotReturnSharesForOtherPosts() {
        // given
        UUID otherPostId = postJpaRepository.save(PostJpaEntity.builder()
                .userId(sharerId)
                .status(PostStatus.PUBLISHED)
                .build()).getId();
        adapter.save(PostShare.of(postId, sharerId, null, ShareType.LINK));
        adapter.save(PostShare.of(otherPostId, sharerId, null, ShareType.LINK));

        // when
        List<PostShare> result = adapter.findByPostId(postId);

        // then
        assertThat(result).hasSize(1);
        assertEquals(postId, result.get(0).getPostId());
    }

   
}
