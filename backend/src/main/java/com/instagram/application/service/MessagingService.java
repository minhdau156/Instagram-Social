package com.instagram.application.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.instagram.domain.exception.ConversationNotFoundException;
import com.instagram.domain.exception.NotConversationMemberException;
import com.instagram.domain.model.Conversation;
import com.instagram.domain.model.ConversationMember;
import com.instagram.domain.model.Message;
import com.instagram.domain.port.in.messaging.AddGroupMemberUseCase;
import com.instagram.domain.port.in.messaging.CreateConversationUseCase;
import com.instagram.domain.port.in.messaging.GetConversationsUseCase;
import com.instagram.domain.port.in.messaging.GetMessagesUseCase;
import com.instagram.domain.port.in.messaging.LeaveConversationUseCase;
import com.instagram.domain.port.in.messaging.MarkReadUseCase;
import com.instagram.domain.port.in.messaging.SendMessageUseCase;
import com.instagram.domain.port.out.ConversationRepository;
import com.instagram.domain.port.out.MessageRepository;

import jakarta.transaction.Transactional;

@Service
public class MessagingService implements
        CreateConversationUseCase,
        GetConversationsUseCase,
        GetMessagesUseCase,
        SendMessageUseCase,
        MarkReadUseCase,
        AddGroupMemberUseCase,
        LeaveConversationUseCase {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public MessagingService(ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            SimpMessagingTemplate simpMessagingTemplate) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @Override
    @Transactional
    public void leaveConversation(LeaveConversationUseCase.Command command) {
        if (!conversationRepository.isMember(command.conversationId(), command.userId())) {
            throw new NotConversationMemberException(command.userId(), command.conversationId());
        }

        conversationRepository.removeMember(command.conversationId(), command.userId());
    }

    @Override
    @Transactional
    public void addGroupMember(AddGroupMemberUseCase.Command command) {
        if (!conversationRepository.findById(command.conversationId()).isPresent()) {
            throw new ConversationNotFoundException(command.conversationId());
        }

        if (!conversationRepository.isMember(command.conversationId(), command.requesterId())) {
            throw new NotConversationMemberException(command.requesterId(), command.conversationId());
        }

        if (conversationRepository.findMember(command.conversationId(), command.requesterId()).get()
                .getRole() != ConversationMember.Role.OWNER) {
            throw new NotConversationMemberException(command.requesterId(), command.conversationId());
        }

        for (UUID newMemberId : command.newMemberIds()) {
            conversationRepository.addMember(command.conversationId(), newMemberId, ConversationMember.Role.MEMBER);
        }
    }

    @Override
    @Transactional
    public void markRead(MarkReadUseCase.Command command) {
        messageRepository.markAsRead(command.conversationId(), command.userId(), OffsetDateTime.now());
    }

    @Override
    @Transactional
    public Message sendMessage(SendMessageUseCase.Command command) {
        if (!conversationRepository.isMember(command.conversationId(), command.senderId())) {
            throw new NotConversationMemberException(command.senderId(), command.conversationId());
        }

        Message message = Message.builder()
                .conversationId(command.conversationId())
                .senderId(command.senderId())
                .content(command.content())
                .messageType(command.messageType())
                .mediaUrl(command.mediaUrl())
                .sharedPostId(command.sharedPostId())
                .status(Message.MessageStatus.SENT)
                .createdAt(OffsetDateTime.now())
                .build();

        Message savedMessage = messageRepository.save(message);
        simpMessagingTemplate.convertAndSend(
                "/topic/conversations/" + command.conversationId(),
                savedMessage);

        return savedMessage;

    }

    @Override
    @Transactional
    public List<Message> getMessages(GetMessagesUseCase.Query query) {
        if (!conversationRepository.isMember(query.conversationId(), query.requesterId())) {
            throw new NotConversationMemberException(query.requesterId(), query.conversationId());
        }

        return messageRepository.findByConversationId(query.conversationId(), query.cursor(), query.limit());
    }

    @Override
    @Transactional
    public List<Conversation> getConversations(GetConversationsUseCase.Query query) {
        return conversationRepository.findByMemberId(query.userId(),
                PageRequest.of(query.page(), query.size()));
    }

    @Override
    @Transactional
    public Conversation createConversation(CreateConversationUseCase.Command command) {
        if (!command.isGroup()) {
            if (command.participantIds().size() != 1) {
                throw new IllegalArgumentException("A 1-to-1 conversation requires exactly one participant");
            }
            Optional<Conversation> existing = conversationRepository.findExisting1to1(
                    command.creatorId(), command.participantIds().get(0));
            if (existing.isPresent()) {
                return existing.get();
            }
        }

        Conversation conversation = Conversation.builder()
                .name(command.name())
                .isGroup(command.isGroup())
                .build();

        Conversation savedConversation = conversationRepository.save(conversation);
        conversationRepository.addMember(savedConversation.getId(), command.creatorId(), ConversationMember.Role.OWNER);

        for (UUID participantId : command.participantIds()) {
            conversationRepository.addMember(savedConversation.getId(), participantId, ConversationMember.Role.MEMBER);
        }

        return savedConversation;
    }

}
