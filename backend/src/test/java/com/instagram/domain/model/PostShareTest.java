package com.instagram.domain.model;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PostShareTest {

    @Test
    void of_createsPostShare_withCorrectFields() {
        UUID postId = UUID.randomUUID();
        UUID sharerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        PostShare postShare = PostShare.of(postId, sharerId, recipientId, ShareType.DM);

        assertThat(postShare.getId()).isNotNull();
        assertThat(postShare.getPostId()).isEqualTo(postId);
        assertThat(postShare.getSharerId()).isEqualTo(sharerId);
        assertThat(postShare.getRecipientId()).isEqualTo(recipientId);
        assertThat(postShare.getShareType()).isEqualTo(ShareType.DM);
        assertThat(postShare.getCreatedAt()).isNotNull();
    }

    @Test
    void of_dmShare_hasRecipientId() {
        UUID postId = UUID.randomUUID();
        UUID sharerId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();

        PostShare postShare = PostShare.of(postId, sharerId, recipientId, ShareType.DM);

        assertThat(postShare.getShareType()).isEqualTo(ShareType.DM);
        assertThat(postShare.getRecipientId()).isNotNull();
        assertThat(postShare.getRecipientId()).isEqualTo(recipientId);
    }

    @Test
    void of_linkShare_recipientIdIsNull() {
        UUID postId = UUID.randomUUID();
        UUID sharerId = UUID.randomUUID();

        PostShare postShare = PostShare.of(postId, sharerId, null, ShareType.LINK);

        assertThat(postShare.getShareType()).isEqualTo(ShareType.LINK);
        assertThat(postShare.getRecipientId()).isNull();
    }

    @Test
    void builder_throwsException_whenShareTypeIsNull() {
        assertThatThrownBy(() -> PostShare.builder()
                .id(UUID.randomUUID())
                .postId(UUID.randomUUID())
                .sharerId(UUID.randomUUID())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("shareType cannot be null");
    }
}
