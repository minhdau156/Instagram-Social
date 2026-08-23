package com.instagram.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.instagram.adapter.out.persistence.repository.IdempotencyKeyJpaRepository;
import com.instagram.domain.exception.NotConversationMemberException;
import com.instagram.domain.model.Conversation;
import com.instagram.domain.model.Message;
import com.instagram.domain.model.User;
import com.instagram.domain.port.in.messaging.AddGroupMemberUseCase;
import com.instagram.domain.port.in.messaging.CreateConversationUseCase;
import com.instagram.domain.port.in.messaging.GetConversationsUseCase;
import com.instagram.domain.port.in.messaging.GetMessagesUseCase;
import com.instagram.domain.port.in.messaging.GetUnreadMessageUseCase;
import com.instagram.domain.port.in.messaging.LeaveConversationUseCase;
import com.instagram.domain.port.in.messaging.MarkReadUseCase;
import com.instagram.domain.port.in.messaging.SendMessageUseCase;
import com.instagram.domain.port.in.user.GetUserUseCase;
import com.instagram.infrastructure.security.HtmlSanitizer;
import com.instagram.infrastructure.security.JwtTokenProvider;
import com.instagram.infrastructure.security.OAuth2SuccessHandler;
import com.instagram.infrastructure.security.SecurityConfig;


@WebMvcTest(MessageController.class)
@Import(SecurityConfig.class)
class MessageControllerIT {

        private static final String CURRENT_USER_ID = "123e4567-e89b-12d3-a456-426614174000";

        @Autowired
        private MockMvc mockMvc;

        @MockBean
        private JwtTokenProvider jwtTokenProvider;
        @MockBean
        private UserDetailsService userDetailsService;
        @MockBean
        private OAuth2SuccessHandler oAuth2SuccessHandler;

        @MockBean
        private IdempotencyKeyJpaRepository idempotencyKeyJpaRepository;

        @MockBean
        private GetConversationsUseCase getConversationsUseCase;
        @MockBean
        private CreateConversationUseCase createConversationUseCase;
        @MockBean
        private GetMessagesUseCase getMessagesUseCase;
        @MockBean
        private SendMessageUseCase sendMessageUseCase;
        @MockBean
        private SimpMessagingTemplate simpMessagingTemplate;
        @MockBean
        private MarkReadUseCase markReadUseCase;
        @MockBean
        private AddGroupMemberUseCase addGroupMemberUseCase;
        @MockBean
        private LeaveConversationUseCase leaveConversationUseCase;

        @MockBean
        private GetUserUseCase getUserUseCase;

        @MockBean 
        private HtmlSanitizer htmlSanitizer;

        @MockBean
        private GetUnreadMessageUseCase getUnreadMessageUseCase;

        // ── helpers ──────────────────────────────────────────────────────────────

        private static Conversation buildConversation() {
                return Conversation.builder()
                                .id(UUID.randomUUID())
                                .name("Test Chat")
                                .isGroup(false)
                                .createdById(UUID.fromString(CURRENT_USER_ID))
                                .createdAt(OffsetDateTime.now())
                                .updatedAt(OffsetDateTime.now())
                                .build();
        }

        private static Message buildMessage(UUID conversationId) {
                return Message.builder()
                                .id(UUID.randomUUID())
                                .conversationId(conversationId)
                                .senderId(UUID.fromString(CURRENT_USER_ID))
                                .content("Hello")
                                .messageType(Message.MessageType.TEXT)
                                .status(Message.MessageStatus.SENT)
                                .createdAt(OffsetDateTime.now())
                                .build();
        }

        // ── tests ─────────────────────────────────────────────────────────────────

        @Test
        @WithMockUser(username = CURRENT_USER_ID)
        void getConversations_returns200WithList() throws Exception {
                Conversation conversation = buildConversation();
                when(getConversationsUseCase.getConversations(any()))
                                .thenReturn(List.of(
                                                new GetConversationsUseCase.ConversationView(conversation, 2, null,
                                                                null)));

                mockMvc.perform(get("/api/v1/conversations"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data[0].id").value(conversation.getId().toString()))
                                .andExpect(jsonPath("$.data[0].unreadCount").value(2));
        }

        @Test
        @WithMockUser(username = CURRENT_USER_ID)
        void createConversation_returns201WithConversation() throws Exception {
                Conversation conversation = buildConversation();
                when(createConversationUseCase.createConversation(any())).thenReturn(conversation);
                when(getUserUseCase.getUser(any())).thenReturn(User.builder().id(UUID.randomUUID()).username("testuser").build());
                String body = """
                                {
                                  "participantIds": ["%s"],
                                  "name": "Test Chat",
                                  "isGroup": false
                                }
                                """.formatted(UUID.randomUUID());

                mockMvc.perform(post("/api/v1/conversations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.id").value(conversation.getId().toString()))
                                .andExpect(jsonPath("$.data.name").value("Test Chat"));
        }

        @Test
        @WithMockUser(username = CURRENT_USER_ID)
        void createConversation_missingParticipants_returns400() throws Exception {
                String body = """
                                {
                                  "participantIds": [],
                                  "isGroup": false
                                }
                                """;

                mockMvc.perform(post("/api/v1/conversations")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = CURRENT_USER_ID)
        void getMessages_returns200WithMessageList() throws Exception {
                UUID conversationId = UUID.randomUUID();
                Message message = buildMessage(conversationId);
                when(getMessagesUseCase.getMessages(any()))
                                .thenReturn(List.of(new GetMessagesUseCase.MessageView(message, "alice", null)));

                mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.data").isArray())
                                .andExpect(jsonPath("$.data[0].content").value("Hello"))
                                .andExpect(jsonPath("$.data[0].senderUsername").value("alice"));
        }

        @Test
        @WithMockUser(username = CURRENT_USER_ID)
        void sendMessage_returns201WithMessage() throws Exception {
                UUID conversationId = UUID.randomUUID();
                Message message = buildMessage(conversationId);
                when(sendMessageUseCase.sendMessage(any()))
                                .thenReturn(new SendMessageUseCase.MessageView(message, "alice", null));

                String body = """
                                {
                                  "content": "Hello",
                                  "messageType": "TEXT"
                                }
                                """;

                mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isCreated())
                                .andExpect(jsonPath("$.data.content").value("Hello"))
                                .andExpect(jsonPath("$.data.messageType").value("TEXT"));
        }

        @Test
        @WithMockUser(username = CURRENT_USER_ID)
        void sendMessage_missingMessageType_returns400() throws Exception {
                UUID conversationId = UUID.randomUUID();

                String body = """
                                {
                                  "content": "Hello"
                                }
                                """;

                mockMvc.perform(post("/api/v1/conversations/{id}/messages", conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isBadRequest());
        }

        @Test
        @WithMockUser(username = CURRENT_USER_ID)
        void markRead_returns204() throws Exception {
                UUID conversationId = UUID.randomUUID();
                UUID messageId = UUID.randomUUID();
                String body = """
                                {
                                  "messageId": "%s"
                                }
                                """.formatted(messageId);

                mockMvc.perform(put("/api/v1/conversations/{id}/read", conversationId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body))
                                .andExpect(status().isNoContent());

                verify(markReadUseCase).markRead(
                                new MarkReadUseCase.Command(conversationId, UUID.fromString(CURRENT_USER_ID),
                                                messageId));
        }

        @Test
        @WithMockUser(username = CURRENT_USER_ID)
        void getMessages_notConversationMember_returns403() throws Exception {
                UUID conversationId = UUID.randomUUID();
                when(getMessagesUseCase.getMessages(any()))
                                .thenThrow(new NotConversationMemberException(
                                                UUID.fromString(CURRENT_USER_ID), conversationId));

                mockMvc.perform(get("/api/v1/conversations/{id}/messages", conversationId))
                                .andExpect(status().isForbidden());
        }
}
