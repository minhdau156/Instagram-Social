package com.instagram.adapter.out.persistence.entity;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CompositeIdTest {

    // ── FollowId ─────────────────────────────────────────────────────────────

    @Test
    void followId_equalWhenSameValues() {
        UUID f = UUID.randomUUID();
        UUID g = UUID.randomUUID();
        assertThat(new FollowId(f, g)).isEqualTo(new FollowId(f, g));
    }

    @Test
    void followId_notEqualWhenDifferentValues() {
        UUID f = UUID.randomUUID();
        UUID g = UUID.randomUUID();
        assertThat(new FollowId(f, g)).isNotEqualTo(new FollowId(g, f));
    }

    @Test
    void followId_sameHashCodeForEqualInstances() {
        UUID f = UUID.randomUUID();
        UUID g = UUID.randomUUID();
        assertThat(new FollowId(f, g).hashCode()).isEqualTo(new FollowId(f, g).hashCode());
    }

    @Test
    void followId_defaultConstructorCreatesNullFields() {
        FollowId id = new FollowId();
        assertThat(id.getFollowerId()).isNull();
        assertThat(id.getFollowingId()).isNull();
    }

    // ── PostLikeId ───────────────────────────────────────────────────────────

    @Test
    void postLikeId_equalWhenSameValues() {
        UUID p = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThat(new PostLikeId(p, u)).isEqualTo(new PostLikeId(p, u));
    }

    @Test
    void postLikeId_notEqualWhenDifferentValues() {
        UUID p = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThat(new PostLikeId(p, u)).isNotEqualTo(new PostLikeId(u, p));
    }

    @Test
    void postLikeId_sameHashCodeForEqualInstances() {
        UUID p = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThat(new PostLikeId(p, u).hashCode()).isEqualTo(new PostLikeId(p, u).hashCode());
    }

    @Test
    void postLikeId_gettersReturnConstructorValues() {
        UUID p = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        PostLikeId id = new PostLikeId(p, u);
        assertThat(id.getPostId()).isEqualTo(p);
        assertThat(id.getUserId()).isEqualTo(u);
    }

    // ── CommentLikeId ────────────────────────────────────────────────────────

    @Test
    void commentLikeId_equalWhenSameValues() {
        UUID c = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThat(new CommentLikeId(c, u)).isEqualTo(new CommentLikeId(c, u));
    }

    @Test
    void commentLikeId_notEqualWhenDifferentValues() {
        UUID c = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThat(new CommentLikeId(c, u)).isNotEqualTo(new CommentLikeId(u, c));
    }

    @Test
    void commentLikeId_sameHashCodeForEqualInstances() {
        UUID c = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThat(new CommentLikeId(c, u).hashCode()).isEqualTo(new CommentLikeId(c, u).hashCode());
    }

    // ── SavePostId ───────────────────────────────────────────────────────────

    @Test
    void savePostId_equalWhenSameValues() {
        UUID p = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThat(new SavePostId(p, u)).isEqualTo(new SavePostId(p, u));
    }

    @Test
    void savePostId_notEqualWhenDifferentValues() {
        UUID p = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThat(new SavePostId(p, u)).isNotEqualTo(new SavePostId(u, p));
    }

    @Test
    void savePostId_settersUpdateValues() {
        SavePostId id = new SavePostId();
        UUID p = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        id.setPostId(p);
        id.setUserId(u);
        assertThat(id.getPostId()).isEqualTo(p);
        assertThat(id.getUserId()).isEqualTo(u);
    }

    // ── PostHashtagId ────────────────────────────────────────────────────────

    @Test
    void postHashtagId_equalWhenSameValues() {
        UUID p = UUID.randomUUID();
        UUID h = UUID.randomUUID();
        assertThat(new PostHashtagId(p, h)).isEqualTo(new PostHashtagId(p, h));
    }

    @Test
    void postHashtagId_notEqualWhenDifferentValues() {
        UUID p = UUID.randomUUID();
        UUID h = UUID.randomUUID();
        assertThat(new PostHashtagId(p, h)).isNotEqualTo(new PostHashtagId(h, p));
    }

    @Test
    void postHashtagId_sameHashCodeForEqualInstances() {
        UUID p = UUID.randomUUID();
        UUID h = UUID.randomUUID();
        assertThat(new PostHashtagId(p, h).hashCode()).isEqualTo(new PostHashtagId(p, h).hashCode());
    }

    // ── UserInterestId ───────────────────────────────────────────────────────

    @Test
    void userInterestId_equalWhenSameValues() {
        UUID u = UUID.randomUUID();
        UUID h = UUID.randomUUID();
        assertThat(new UserInterestId(u, h)).isEqualTo(new UserInterestId(u, h));
    }

    @Test
    void userInterestId_notEqualWhenDifferentValues() {
        UUID u = UUID.randomUUID();
        UUID h = UUID.randomUUID();
        assertThat(new UserInterestId(u, h)).isNotEqualTo(new UserInterestId(h, u));
    }

    @Test
    void userInterestId_sameHashCodeForEqualInstances() {
        UUID u = UUID.randomUUID();
        UUID h = UUID.randomUUID();
        assertThat(new UserInterestId(u, h).hashCode()).isEqualTo(new UserInterestId(u, h).hashCode());
    }

    // ── MessageReadId ────────────────────────────────────────────────────────

    @Test
    void messageReadId_equalWhenSameValues() {
        UUID m = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThat(new MessageReadId(m, u)).isEqualTo(new MessageReadId(m, u));
    }

    @Test
    void messageReadId_notEqualWhenDifferentValues() {
        UUID m = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThat(new MessageReadId(m, u)).isNotEqualTo(new MessageReadId(u, m));
    }

    @Test
    void messageReadId_defaultConstructorWorks() {
        MessageReadId id = new MessageReadId();
        assertThat(id.getMessageId()).isNull();
        assertThat(id.getUserId()).isNull();
    }

    // ── ConversationMemberId ─────────────────────────────────────────────────

    @Test
    void conversationMemberId_equalWhenSameValues() {
        UUID c = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThat(new ConversationMemberId(c, u)).isEqualTo(new ConversationMemberId(c, u));
    }

    @Test
    void conversationMemberId_notEqualWhenDifferentValues() {
        UUID c = UUID.randomUUID();
        UUID u = UUID.randomUUID();
        assertThat(new ConversationMemberId(c, u)).isNotEqualTo(new ConversationMemberId(u, c));
    }

    @Test
    void conversationMemberId_defaultConstructorWorks() {
        ConversationMemberId id = new ConversationMemberId();
        assertThat(id.getConversationId()).isNull();
        assertThat(id.getUserId()).isNull();
    }

    // ── UserBlockId ──────────────────────────────────────────────────────────

    @Test
    void userBlockId_equalWhenSameValues() {
        UUID b = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        assertThat(new UserBlockId(b, d)).isEqualTo(new UserBlockId(b, d));
    }

    @Test
    void userBlockId_notEqualWhenDifferentValues() {
        UUID b = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        assertThat(new UserBlockId(b, d)).isNotEqualTo(new UserBlockId(d, b));
    }

    @Test
    void userBlockId_settersUpdateValues() {
        UserBlockId id = new UserBlockId();
        UUID b = UUID.randomUUID();
        UUID d = UUID.randomUUID();
        id.setBlockerId(b);
        id.setBlockedId(d);
        assertThat(id.getBlockerId()).isEqualTo(b);
        assertThat(id.getBlockedId()).isEqualTo(d);
    }

    // ── UserRoleId ───────────────────────────────────────────────────────────

    @Test
    void userRoleId_equalWhenSameValues() {
        UUID u = UUID.randomUUID();
        UUID r = UUID.randomUUID();
        assertThat(new UserRoleId(u, r)).isEqualTo(new UserRoleId(u, r));
    }

    @Test
    void userRoleId_notEqualWhenDifferentValues() {
        UUID u = UUID.randomUUID();
        UUID r = UUID.randomUUID();
        assertThat(new UserRoleId(u, r)).isNotEqualTo(new UserRoleId(r, u));
    }

    @Test
    void userRoleId_sameHashCodeForEqualInstances() {
        UUID u = UUID.randomUUID();
        UUID r = UUID.randomUUID();
        assertThat(new UserRoleId(u, r).hashCode()).isEqualTo(new UserRoleId(u, r).hashCode());
    }

    @Test
    void userRoleId_notEqualToNull() {
        UUID u = UUID.randomUUID();
        UUID r = UUID.randomUUID();
        assertThat(new UserRoleId(u, r)).isNotEqualTo(null);
    }
}
