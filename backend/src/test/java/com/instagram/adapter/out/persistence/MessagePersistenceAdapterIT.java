package com.instagram.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.instagram.adapter.out.persistence.entity.ConversationJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserJpaEntity;
import com.instagram.adapter.out.persistence.repository.ConversationJpaRepository;
import com.instagram.adapter.out.persistence.repository.MessageJpaRepository;
import com.instagram.adapter.out.persistence.repository.MessageReadJpaRepository;
import com.instagram.adapter.out.persistence.repository.UserJpaRepository;
import com.instagram.domain.model.Message;
import com.instagram.domain.model.PrivacyLevel;
import com.instagram.domain.model.UserStatus;

public class MessagePersistenceAdapterIT extends PostgresIntegrationTest {

    @Autowired
    private MessageJpaRepository messageJpaRepository;

    @Autowired
    private MessageReadJpaRepository messageReadJpaRepository;

    @Autowired
    private ConversationJpaRepository conversationJpaRepository;

    @Autowired
    private UserJpaRepository userJpaRepository;

    private MessagePersistenceAdapter adapter;
    private UUID conversationId;
    private UUID senderId;

    // messages.conversation_id / messages.sender_id are real FKs — every test
    // needs an actually-persisted parent row rather than a bare
    // UUID.randomUUID().
    private UUID persistUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        return userJpaRepository.save(UserJpaEntity.builder()
                .username("msg_user_" + suffix)
                .email("msg_user_" + suffix + "@example.com")
                .fullName("Message User")
                .status(UserStatus.ACTIVE)
                .privacyLevel(PrivacyLevel.PUBLIC)
                .isVerified(false)
                .build()).getId();
    }

    private UUID persistConversation(UUID creatorId) {
        return conversationJpaRepository.save(ConversationJpaEntity.builder()
                .isGroup(false)
                .createdById(creatorId)
                .build()).getId();
    }

    @BeforeEach
    void setUp() {
        adapter = new MessagePersistenceAdapter(messageJpaRepository, messageReadJpaRepository);
        senderId = persistUser();
        conversationId = persistConversation(senderId);
    }

    private Message buildMessage(UUID convId, UUID sender) {
        return Message.builder()
                .conversationId(convId)
                .senderId(sender)
                .content("Hello World")
                .messageType(Message.MessageType.TEXT)
                .build();
    }

    @Test
    void save_whenSuccess_persistsMessageWithCorrectFields() {
        // given
        Message message = buildMessage(conversationId, senderId);

        // when
        Message saved = adapter.save(message);

        // then
        assertNotNull(saved.getId());
        assertEquals(conversationId, saved.getConversationId());
        assertEquals(senderId, saved.getSenderId());
        assertEquals("Hello World", saved.getContent());
        assertEquals(Message.MessageType.TEXT, saved.getMessageType());
        assertEquals(Message.MessageStatus.SENT, saved.getStatus());
    }

    @Test
    void findById_whenExists_returnsMessage() {
        // given
        Message saved = adapter.save(buildMessage(conversationId, senderId));

        // when
        Optional<Message> found = adapter.findById(saved.getId());

        // then
        assertTrue(found.isPresent());
        assertEquals(saved.getId(), found.get().getId());
        assertEquals(saved.getContent(), found.get().getContent());
        assertEquals(saved.getConversationId(), found.get().getConversationId());
    }

    @Test
    void findById_whenNotExists_returnsEmpty() {
        // given / when
        Optional<Message> found = adapter.findById(UUID.randomUUID());

        // then
        assertTrue(found.isEmpty());
    }

    @Test
    void findByConversationId_withNullCursor_returnsAllMessages() {
        // given
        adapter.save(buildMessage(conversationId, senderId));
        adapter.save(buildMessage(conversationId, senderId));

        // when
        List<Message> messages = adapter.findByConversationId(conversationId, null, 10);

        // then
        assertThat(messages).hasSize(2);
        assertThat(messages).allMatch(m -> m.getConversationId().equals(conversationId));
    }

    @Test
    void findByConversationId_withNullCursor_respectsLimit() {
        // given
        for (int i = 0; i < 5; i++) {
            adapter.save(buildMessage(conversationId, senderId));
        }

        // when
        List<Message> messages = adapter.findByConversationId(conversationId, null, 3);

        // then
        assertThat(messages).hasSize(3);
    }

    @Test
    void findByConversationId_withCursor_returnsOlderMessages() throws InterruptedException {
        // given
        Message older = adapter.save(buildMessage(conversationId, senderId));
        Thread.sleep(10);
        Message newer = adapter.save(buildMessage(conversationId, senderId));

        // when
        List<Message> result = adapter.findByConversationId(conversationId, newer.getId(), 10);

        // then
        assertThat(result).hasSize(1);
        assertEquals(older.getId(), result.get(0).getId());
    }

    @Test
    void findByConversationId_withCursor_doesNotIncludeNewerMessages() throws InterruptedException {
        // given
        Message cursor = adapter.save(buildMessage(conversationId, senderId));
        Thread.sleep(10);
        adapter.save(buildMessage(conversationId, senderId));

        // when
        List<Message> result = adapter.findByConversationId(conversationId, cursor.getId(), 10);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void findLatestByConversationId_whenMessagesExist_returnsLatest() throws InterruptedException {
        // given
        adapter.save(buildMessage(conversationId, senderId));
        Thread.sleep(10);
        Message latest = adapter.save(buildMessage(conversationId, senderId));

        // when
        Optional<Message> found = adapter.findLatestByConversationId(conversationId);

        // then
        assertTrue(found.isPresent());
        assertEquals(latest.getId(), found.get().getId());
    }

    @Test
    void findLatestByConversationId_whenNoMessages_returnsEmpty() {
        // given / when
        Optional<Message> found = adapter.findLatestByConversationId(UUID.randomUUID());

        // then
        assertTrue(found.isEmpty());
    }

    @Test
    void getUnreadCount_whenNoMessages_returnsZero() {
        // given / when
        int count = adapter.getUnreadCount(conversationId, UUID.randomUUID());

        // then
        assertEquals(0, count);
    }

    @Test
    void getUnreadCount_countsOnlyMessagesSentByOthers() {
        // given
        UUID readerId = UUID.randomUUID();
        UUID otherSender = persistUser();
        adapter.save(Message.builder()
                .conversationId(conversationId)
                .senderId(otherSender)
                .content("from other sender")
                .messageType(Message.MessageType.TEXT)
                .build());

        // when
        int count = adapter.getUnreadCount(conversationId, readerId);

        // then
        assertEquals(1, count);
    }

    @Test
    void getUnreadCount_doesNotCountOwnMessages() {
        // given
        adapter.save(Message.builder()
                .conversationId(conversationId)
                .senderId(senderId)
                .content("my own message")
                .messageType(Message.MessageType.TEXT)
                .build());

        // when
        int count = adapter.getUnreadCount(conversationId, senderId);

        // then
        assertEquals(0, count);
    }

    @Test
    void getUnreadCount_withMultipleMessages_countsAll() {
        // given
        UUID readerId = UUID.randomUUID();
        UUID otherSender = persistUser();
        for (int i = 0; i < 3; i++) {
            adapter.save(Message.builder()
                    .conversationId(conversationId)
                    .senderId(otherSender)
                    .content("msg " + i)
                    .messageType(Message.MessageType.TEXT)
                    .build());
        }

        // when
        int count = adapter.getUnreadCount(conversationId, readerId);

        // then
        assertEquals(3, count);
    }
}
