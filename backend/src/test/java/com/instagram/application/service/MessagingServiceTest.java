package com.instagram.application.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.instagram.domain.exception.ConversationNotFoundException;
import com.instagram.domain.exception.NotConversationMemberException;
import com.instagram.domain.model.Conversation;
import com.instagram.domain.model.ConversationMember;
import com.instagram.domain.model.Message;
import com.instagram.domain.model.Message.MessageType;
import com.instagram.domain.model.User;
import com.instagram.domain.port.in.messaging.AddGroupMemberUseCase;
import com.instagram.domain.port.in.messaging.CreateConversationUseCase;
import com.instagram.domain.port.in.messaging.GetConversationsUseCase;
import com.instagram.domain.port.in.messaging.GetMessagesUseCase;
import com.instagram.domain.port.in.messaging.LeaveConversationUseCase;
import com.instagram.domain.port.in.messaging.MarkReadUseCase;
import com.instagram.domain.port.in.messaging.SendMessageUseCase;
import com.instagram.domain.port.out.ConversationRepository;
import com.instagram.domain.port.out.MessageRepository;
import com.instagram.domain.port.out.UserRepository;

@ExtendWith(MockitoExtension.class)
class MessagingServiceTest {
        @Mock
        private ConversationRepository conversationRepository;
        @Mock
        private MessageRepository messageRepository;
        @Mock
        private UserRepository userRepository;
        @InjectMocks
        private MessagingService messagingService;

        @Test
        void createConversation_withTwoUsers_returnsConversationWithConversationId() {
                // Given
                UUID userOneId = UUID.randomUUID();
                UUID userTwoId = UUID.randomUUID();
                UUID creatorId = UUID.randomUUID();
                CreateConversationUseCase.Command command = new CreateConversationUseCase.Command(
                                creatorId, List.of(userOneId, userTwoId), "haha", true);
                Conversation conversation = Conversation.builder()
                                .id(UUID.randomUUID())
                                .name("haha")
                                .isGroup(true)
                                .build();
                when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);
                doNothing().when(conversationRepository).addMember(any(), any(), any());
                // When
                messagingService.createConversation(command);
                // Then
                verify(conversationRepository, times(1)).save(any(Conversation.class));
                verify(conversationRepository, times(3)).addMember(any(), any(), any());

        }

        @Test
        void createConversation_isNotGroupConversation_returnsConversationWithConversationId() {
                // Given
                UUID userOneId = UUID.randomUUID();
                UUID creatorId = UUID.randomUUID();
                CreateConversationUseCase.Command command = new CreateConversationUseCase.Command(
                                creatorId, List.of(userOneId), "haha", false);
                Conversation conversation = Conversation.builder()
                                .id(UUID.randomUUID())
                                .name("haha")
                                .isGroup(false)
                                .build();
                when(conversationRepository.findExisting1to1(any(), any())).thenReturn(Optional.of(conversation));

                // When
                messagingService.createConversation(command);
                // Then
                verify(conversationRepository, times(1)).findExisting1to1(any(), any());

        }

        @Test
        void createConversation_isNotGroupConversationAndIsNew_savesNewConversation() {
                // Given
                UUID creatorId = UUID.randomUUID();
                UUID participantId = UUID.randomUUID();
                CreateConversationUseCase.Command command = new CreateConversationUseCase.Command(
                                creatorId, List.of(participantId), null, false);
                Conversation conversation = Conversation.builder()
                                .id(UUID.randomUUID())
                                .isGroup(false)
                                .build();
                when(conversationRepository.findExisting1to1(any(), any())).thenReturn(Optional.empty());
                when(conversationRepository.save(any(Conversation.class))).thenReturn(conversation);
                doNothing().when(conversationRepository).addMember(any(), any(), any());

                // When
                messagingService.createConversation(command);

                // Then
                verify(conversationRepository, times(1)).save(any(Conversation.class));
                verify(conversationRepository, times(2)).addMember(any(), any(), any());
        }

        @Test
        void createConversation_isNotGroupConversationAndMemberIsMoreThan1_throwException() {
                // Given
                UUID creatorId = UUID.randomUUID();
                CreateConversationUseCase.Command command = new CreateConversationUseCase.Command(
                                creatorId, List.of(), "haha", false);

                // When
                assertThrows(IllegalArgumentException.class, () -> messagingService.createConversation(command));

        }

        @Test
        void getConversations_isTrue_returnsConversationsWithConversationId() {
                // Given
                UUID userId = UUID.randomUUID();
                Conversation conversation = Conversation.builder()
                                .id(UUID.randomUUID())
                                .name("haha")
                                .isGroup(true)
                                .build();
                GetConversationsUseCase.Query query = new GetConversationsUseCase.Query(userId, 0, 10);
                when(conversationRepository.findByMemberId(any(), any())).thenReturn(List.of(conversation));
                // When
                List<GetConversationsUseCase.ConversationView> res = messagingService.getConversations(query);

                assertEquals(1, res.size());
                assertEquals(conversation.getId(), res.get(0).conversation().getId());
                assertEquals(0, res.get(0).unreadCount());
        }

        @Test
        void getMessages_isSuccess_returnsMessagesWithMessagesId() {
                // Given
                UUID conversationId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                GetMessagesUseCase.Query query = new GetMessagesUseCase.Query(conversationId, userId, null, 10);
                Message message = Message.builder()
                                .id(UUID.randomUUID())
                                .content("haha")
                                .senderId(userId)
                                .build();
                User user = User.builder()
                                .id(userId)
                                .username("haha")
                                .profilePictureUrl("haha")
                                .build();
                when(conversationRepository.isMember(any(), any())).thenReturn(true);
                when(messageRepository.findByConversationId(any(), any(), anyInt())).thenReturn(List.of(message));
                when(userRepository.findAllByIds(any())).thenReturn(List.of(user));
                // When
                List<GetMessagesUseCase.MessageView> res = messagingService.getMessages(query);

                assertEquals(1, res.size());
                assertEquals(message.getId(), res.get(0).message().getId());
                assertEquals("haha", res.get(0).senderUsername());
                assertEquals("haha", res.get(0).senderAvatarUrl());
        }

        @Test
        void getMessages_isNotMember_throwException() {
                // Given
                UUID conversationId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                GetMessagesUseCase.Query query = new GetMessagesUseCase.Query(conversationId, userId, null, 10);
                when(conversationRepository.isMember(any(), any())).thenReturn(false);
                // When
                assertThrows(NotConversationMemberException.class, () -> messagingService.getMessages(query));

        }

        @Test
        void sendMessage_isSuccess_returnsMessageView() {
                // Given
                UUID conversationId = UUID.randomUUID();
                UUID senderId = UUID.randomUUID();
                SendMessageUseCase.Command command = new SendMessageUseCase.Command(conversationId, senderId, "haha",
                                MessageType.TEXT, null, null);
                Message message = Message.builder()
                                .id(UUID.randomUUID())
                                .content("haha")
                                .senderId(senderId)
                                .build();
                User user = User.builder()
                                .id(senderId)
                                .username("haha")
                                .profilePictureUrl("haha")
                                .build();
                when(conversationRepository.isMember(any(), any())).thenReturn(true);
                when(messageRepository.save(any())).thenReturn(message);
                when(userRepository.findById(any())).thenReturn(Optional.of(user));
                // When
                SendMessageUseCase.MessageView res = messagingService.sendMessage(command);
                // Then
                assertEquals(message.getId(), res.message().getId());
                assertEquals("haha", res.senderUsername());
                assertEquals("haha", res.senderAvatarUrl());

        }

        @Test
        void sendMessage_isNotMember_throwException() {
                // Given
                UUID conversationId = UUID.randomUUID();
                UUID senderId = UUID.randomUUID();
                SendMessageUseCase.Command command = new SendMessageUseCase.Command(conversationId, senderId, "haha",
                                MessageType.TEXT, null, null);
                when(conversationRepository.isMember(any(), any())).thenReturn(false);
                // When
                assertThrows(NotConversationMemberException.class, () -> messagingService.sendMessage(command));
        }

        @Test
        void markRead_isSuccess_marksMessageAsRead() {
                // Given
                UUID conversationId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                UUID messageId = UUID.randomUUID();
                MarkReadUseCase.Command command = new MarkReadUseCase.Command(conversationId, userId, messageId);
                doNothing().when(messageRepository).markAsRead(any(), any(), any(), any());
                // When
                messagingService.markRead(command);
                // Then
                verify(messageRepository).markAsRead(eq(conversationId), eq(messageId), eq(userId), any());
        }

        @Test
        void addGroupMember_isSuccess_addsGroupMember() {
                // Given
                UUID conversationId = UUID.randomUUID();
                UUID requesterId = UUID.randomUUID();
                UUID newMemberId = UUID.randomUUID();
                AddGroupMemberUseCase.Command command = new AddGroupMemberUseCase.Command(conversationId, requesterId,
                                List.of(newMemberId));
                Conversation conversation = Conversation.builder()
                                .id(conversationId)
                                .name("haha")
                                .isGroup(true)
                                .build();
                when(conversationRepository.findById(any())).thenReturn(Optional.of(conversation));
                when(conversationRepository.isMember(any(), any())).thenReturn(true);
                when(conversationRepository.findMember(any(), any())).thenReturn(Optional.of(
                                ConversationMember.builder()
                                                .role(ConversationMember.Role.OWNER)
                                                .build()));
                doNothing().when(conversationRepository).addMember(any(), any(), any());
                // When
                messagingService.addGroupMember(command);
                // Then
                verify(conversationRepository).addMember(eq(conversationId), eq(newMemberId),
                                eq(ConversationMember.Role.MEMBER));
        }

        @Test
        void addGroupMember_isConversationNotFound_throwException() {
                // Given
                UUID conversationId = UUID.randomUUID();
                UUID requesterId = UUID.randomUUID();
                UUID newMemberId = UUID.randomUUID();
                AddGroupMemberUseCase.Command command = new AddGroupMemberUseCase.Command(conversationId, requesterId,
                                List.of(newMemberId));
                when(conversationRepository.findById(any())).thenReturn(Optional.empty());
                // When
                assertThrows(ConversationNotFoundException.class, () -> messagingService.addGroupMember(command));
        }

        @Test
        void addGroupMember_isNotMember_throwException() {
                // Given
                UUID conversationId = UUID.randomUUID();
                UUID requesterId = UUID.randomUUID();
                UUID newMemberId = UUID.randomUUID();
                AddGroupMemberUseCase.Command command = new AddGroupMemberUseCase.Command(conversationId, requesterId,
                                List.of(newMemberId));
                Conversation conversation = Conversation.builder()
                                .id(conversationId)
                                .name("haha")
                                .isGroup(true)
                                .build();
                when(conversationRepository.findById(any())).thenReturn(Optional.of(conversation));
                when(conversationRepository.isMember(any(), any())).thenReturn(false);
                // When
                assertThrows(NotConversationMemberException.class, () -> messagingService.addGroupMember(command));
        }

        @Test
        void addGroupMember_isNotOwner_throwException() {
                // Given
                UUID conversationId = UUID.randomUUID();
                UUID requesterId = UUID.randomUUID();
                UUID newMemberId = UUID.randomUUID();
                AddGroupMemberUseCase.Command command = new AddGroupMemberUseCase.Command(conversationId, requesterId,
                                List.of(newMemberId));
                Conversation conversation = Conversation.builder()
                                .id(conversationId)
                                .name("haha")
                                .isGroup(true)
                                .build();
                when(conversationRepository.findById(any())).thenReturn(Optional.of(conversation));
                when(conversationRepository.isMember(any(), any())).thenReturn(true);
                when(conversationRepository.findMember(any(), any())).thenReturn(Optional.of(
                                ConversationMember.builder()
                                                .role(ConversationMember.Role.MEMBER)
                                                .build()));
                // When
                assertThrows(NotConversationMemberException.class, () -> messagingService.addGroupMember(command));
        }

        @Test
        void leaveConversation_isSuccess_returnNoThing() {
                // Given
                UUID conversationId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                LeaveConversationUseCase.Command command = new LeaveConversationUseCase.Command(conversationId, userId);
                when(conversationRepository.isMember(any(), any())).thenReturn(true);
                doNothing().when(conversationRepository).removeMember(any(), any());
                // When
                messagingService.leaveConversation(command);
                // Then
                verify(conversationRepository).removeMember(eq(conversationId), eq(userId));
        }

        @Test
        void leaveConversation_isNotMember_throwException() {
                // Given
                UUID conversationId = UUID.randomUUID();
                UUID userId = UUID.randomUUID();
                LeaveConversationUseCase.Command command = new LeaveConversationUseCase.Command(conversationId, userId);
                when(conversationRepository.isMember(any(), any())).thenReturn(false);
                // When
                assertThrows(NotConversationMemberException.class, () -> messagingService.leaveConversation(command));
        }

}
