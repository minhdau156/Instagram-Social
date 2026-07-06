package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import com.instagram.adapter.out.persistence.entity.MessageJpaEntity;
import com.instagram.domain.model.Message;
import com.instagram.infrastructure.config.JpaConfig;

@DataJpaTest
@Import(JpaConfig.class)
@TestPropertySource(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MessageJpaEntityIT {

    @Autowired
    private TestEntityManager entityManager;

    private MessageJpaEntity minimalMessage() {
        return MessageJpaEntity.builder()
                .conversationId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
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
                    .conversationId(UUID.randomUUID())
                    .senderId(UUID.randomUUID())
                    .messageType(type)
                    .build();

            MessageJpaEntity saved = entityManager.persistFlushFind(msg);
            assertThat(saved.getMessageType()).isEqualTo(type);
        }
    }

    @Test
    void shouldAllowNullableFields() {
        MessageJpaEntity msg = MessageJpaEntity.builder()
                .conversationId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
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
        UUID replyTarget = UUID.randomUUID();
        MessageJpaEntity msg = MessageJpaEntity.builder()
                .conversationId(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .messageType(Message.MessageType.TEXT)
                .content("reply")
                .replyToMessageId(replyTarget)
                .build();

        MessageJpaEntity saved = entityManager.persistFlushFind(msg);

        assertThat(saved.getReplyToMessageId()).isEqualTo(replyTarget);
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
