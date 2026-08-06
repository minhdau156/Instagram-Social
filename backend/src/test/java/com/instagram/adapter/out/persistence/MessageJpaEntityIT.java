package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import com.instagram.adapter.out.persistence.entity.ConversationJpaEntity;
import com.instagram.adapter.out.persistence.entity.MessageJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.domain.model.Message;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.UserStatus;

class MessageJpaEntityIT extends PostgresIntegrationTest {

    @Autowired
    private TestEntityManager entityManager;

    // messages.conversation_id / sender_id are real, NOT NULL FKs under
    // Postgres — every message needs an actually-persisted parent
    // conversation + sender user rather than bare UUID.randomUUID() values.
    private UUID senderId;
    private UUID conversationId;

    @BeforeEach
    void setUp() {
        senderId = persistUser();
        conversationId = persistConversation(senderId);
    }

    private UUID persistUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return entityManager.persistAndFlush(UserJpaEntity.builder()
                .username("msg_user_" + suffix)
                .email("msg_user_" + suffix + "@example.com")
                .fullName("Message User")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build()).getId();
    }

    private UUID persistConversation(UUID creatorId) {
        return entityManager.persistAndFlush(ConversationJpaEntity.builder()
                .isGroup(false)
                .createdById(creatorId)
                .build()).getId();
    }

    private MessageJpaEntity minimalMessage() {
        return MessageJpaEntity.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .messageType(Message.MessageType.TEXT)
                .content("Hello!")
                .build();
    }

    @Test
    void shouldPersistAndRetrieveWithRequiredFields() {
        MessageJpaEntity saved = entityManager.persistFlushFind(minimalMessage());

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getConversationId()).isNotNull();
        assertThat(saved.getSenderId()).isNotNull();
        assertThat(saved.getMessageType()).isEqualTo(Message.MessageType.TEXT);
        assertThat(saved.getContent()).isEqualTo("Hello!");
    }

    @Test
    void shouldAutoPopulateCreatedAtAndUpdatedAt() {
        MessageJpaEntity saved = entityManager.persistFlushFind(minimalMessage());

        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    void shouldPersistAllMessageTypeEnumValues() {
        for (Message.MessageType type : Message.MessageType.values()) {
            MessageJpaEntity msg = MessageJpaEntity.builder()
                    .conversationId(conversationId)
                    .senderId(senderId)
                    .messageType(type)
                    .build();

            MessageJpaEntity saved = entityManager.persistFlushFind(msg);
            assertThat(saved.getMessageType()).isEqualTo(type);
        }
    }

    @Test
    void shouldAllowNullableFields() {
        MessageJpaEntity msg = MessageJpaEntity.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .messageType(Message.MessageType.IMAGE)
                .mediaUrl("https://cdn.example.com/image.jpg")
                .build();

        MessageJpaEntity saved = entityManager.persistFlushFind(msg);

        assertThat(saved.getContent()).isNull();
        assertThat(saved.getSharedPostId()).isNull();
        assertThat(saved.getReplyToMessageId()).isNull();
        assertThat(saved.getMediaUrl()).isEqualTo("https://cdn.example.com/image.jpg");
    }

    @Test
    void shouldPersistReplyToMessageId() {
        // messages.reply_to_message_id is a self-referencing FK — it must
        // point at a real, already-persisted message row.
        MessageJpaEntity original = entityManager.persistFlushFind(minimalMessage());

        MessageJpaEntity msg = MessageJpaEntity.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .messageType(Message.MessageType.TEXT)
                .content("reply")
                .replyToMessageId(original.getId())
                .build();

        MessageJpaEntity saved = entityManager.persistFlushFind(msg);

        assertThat(saved.getReplyToMessageId()).isEqualTo(original.getId());
    }

    @Test
    void shouldGenerateUniqueIdPerMessage() {
        MessageJpaEntity msg1 = entityManager.persistFlushFind(minimalMessage());
        MessageJpaEntity msg2 = entityManager.persistFlushFind(minimalMessage());

        assertThat(msg1.getId()).isNotEqualTo(msg2.getId());
    }

    @Test
    void shouldDefaultIsDeletedToFalse() {
        MessageJpaEntity saved = entityManager.persistFlushFind(minimalMessage());

        assertThat(saved.isDeleted()).isFalse();
    }
}
