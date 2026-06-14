package com.instagram.application.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.instagram.domain.exception.ConversationNotFoundException;
import com.instagram.domain.exception.NotConversationMemberException;
import com.instagram.domain.exception.UserNotFoundException;
import com.instagram.domain.model.User;
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
import com.instagram.domain.port.out.UserRepository;

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
    private final UserRepository userRepository;

    public MessagingService(ConversationRepository conversationRepository,
            MessageRepository messageRepository,
            UserRepository userRepository) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
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
        messageRepository.markAsRead(command.conversationId(), command.messageId(), command.userId(),
                OffsetDateTime.now());
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "conversations", allEntries = true)
    })
    public SendMessageUseCase.MessageView sendMessage(SendMessageUseCase.Command command) {
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

        User sender = userRepository.findById(command.senderId())
                .orElseThrow(() -> UserNotFoundException.withId(command.senderId()));
        return new SendMessageUseCase.MessageView(savedMessage, sender.getUsername(), sender.getProfilePictureUrl());
    }

    @Override
    @Transactional
    public List<GetMessagesUseCase.MessageView> getMessages(GetMessagesUseCase.Query query) {
        if (!conversationRepository.isMember(query.conversationId(), query.requesterId())) {
            throw new NotConversationMemberException(query.requesterId(), query.conversationId());
        }

        List<Message> messages = messageRepository.findByConversationId(
                query.conversationId(), query.cursor(), query.limit());

        List<UUID> senderIds = messages.stream().map(Message::getSenderId).distinct().toList();
        Map<UUID, User> senderMap = userRepository.findAllByIds(senderIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        return messages.stream()
                .map(m -> {
                    User sender = senderMap.get(m.getSenderId());
                    return new GetMessagesUseCase.MessageView(m,
                            sender != null ? sender.getUsername() : null,
                            sender != null ? sender.getProfilePictureUrl() : null);
                })
                .toList();
    }

    @Override
    @Transactional
    @Cacheable(value = "conversations", key = "'conversations:' + #query.userId")
    public List<GetConversationsUseCase.ConversationView> getConversations(GetConversationsUseCase.Query query) {
        List<Conversation> conversations = conversationRepository.findByMemberId(query.userId(),
                PageRequest.of(query.page(), query.size()));
        List<UUID> conversationIds = conversations.stream().map(Conversation::getId).toList();

        Map<UUID, Message> lastMessages = new HashMap<>();

        List<Message> latestMessages = messageRepository.findLatestByConversationIds(conversationIds);

        for (Message m : latestMessages) {
            lastMessages.put(m.getConversationId(), m);
        }

        // For 1-1 conversations, resolve the other member's ID
        Map<UUID, List<UUID>> conversationToOtherMember = conversationRepository
                .findMemberIdsByConversationIds(conversationIds);

        Set<UUID> allUserIds = new HashSet<>(
                lastMessages.values().stream().map(Message::getSenderId).toList());
        allUserIds.addAll(conversationToOtherMember.values().stream().flatMap(List::stream).toList());

        Map<UUID, User> userMap = userRepository.findAllByIds(new ArrayList<>(allUserIds)).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        Map<UUID, Long> unreadCounts = messageRepository.getUnreadCountsByConversationIds(conversationIds,
                query.userId());

        return conversations.stream()
                .map(c -> {
                    Message lastMsg = lastMessages.get(c.getId());
                    GetConversationsUseCase.MessageView lastMessageView = null;
                    if (lastMsg != null) {
                        User sender = userMap.get(lastMsg.getSenderId());
                        lastMessageView = new GetConversationsUseCase.MessageView(
                                lastMsg,
                                sender != null ? sender.getUsername() : null,
                                sender != null ? sender.getProfilePictureUrl() : null);
                    }
                    String eachOtherName = null;
                    if (!c.getIsGroup()) {
                        UUID otherId = conversationToOtherMember.get(c.getId()).stream()
                                .filter(id -> !id.equals(query.userId()))
                                .findFirst().get();
                        User other = otherId != null ? userMap.get(otherId) : null;
                        eachOtherName = other != null ? other.getUsername() : null;
                    }
                    return new GetConversationsUseCase.ConversationView(
                            c,
                            unreadCounts.getOrDefault(c.getId(), 0L),
                            lastMessageView,
                            eachOtherName);
                })
                .toList();
    }

    @Override
    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "conversations", allEntries = true)
    })
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
                .createdById(command.creatorId())
                .build();

        Conversation savedConversation = conversationRepository.save(conversation);
        conversationRepository.addMember(savedConversation.getId(), command.creatorId(), ConversationMember.Role.OWNER);

        for (UUID participantId : command.participantIds()) {
            conversationRepository.addMember(savedConversation.getId(), participantId, ConversationMember.Role.MEMBER);
        }

        return savedConversation;
    }

}
