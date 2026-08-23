package com.instagram.adapter.in.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.instagram.adapter.in.web.dto.request.AddGroupMemberRequest;
import com.instagram.adapter.in.web.dto.request.CreateConversationRequest;
import com.instagram.adapter.in.web.dto.request.MarkReadRequest;
import com.instagram.adapter.in.web.dto.request.SendMessageRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.ConversationResponse;
import com.instagram.adapter.in.web.dto.response.MarkReadResponse;
import com.instagram.adapter.in.web.dto.response.MessageResponse;
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

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Slf4j
@Tag(name = "Messaging", description = "Conversation and message management")
@RequestMapping("/api/v1/conversations")
@RestController
public class MessageController {

        private final GetConversationsUseCase getConversationsUseCase;
        private final CreateConversationUseCase createConversationUseCase;
        private final GetUserUseCase getUserUseCase;
        private final GetMessagesUseCase getMessagesUseCase;
        private final SendMessageUseCase sendMessageUseCase;
        private final MarkReadUseCase markReadUseCase;
        private final AddGroupMemberUseCase addGroupMemberUseCase;
        private final LeaveConversationUseCase leaveConversationUseCase;
        private final SimpMessagingTemplate messagingTemplate;
        private final HtmlSanitizer htmlSanitizer;
        private final GetUnreadMessageUseCase getUnreadMessageUseCase;

        public MessageController(
                        GetConversationsUseCase getConversationsUseCase,
                        CreateConversationUseCase createConversationUseCase,
                        GetMessagesUseCase getMessagesUseCase,
                        SendMessageUseCase sendMessageUseCase,
                        MarkReadUseCase markReadUseCase,
                        AddGroupMemberUseCase addGroupMemberUseCase,
                        LeaveConversationUseCase leaveConversationUseCase,
                        GetUserUseCase getUserUseCase,
                        SimpMessagingTemplate messagingTemplate,
                        HtmlSanitizer htmlSanitizer,
                    GetUnreadMessageUseCase getUnreadMessageUseCase) {
                this.getConversationsUseCase = getConversationsUseCase;
                this.createConversationUseCase = createConversationUseCase;
                this.getMessagesUseCase = getMessagesUseCase;
                this.sendMessageUseCase = sendMessageUseCase;
                this.markReadUseCase = markReadUseCase;
                this.addGroupMemberUseCase = addGroupMemberUseCase;
                this.leaveConversationUseCase = leaveConversationUseCase;
                this.getUserUseCase = getUserUseCase;
                this.messagingTemplate = messagingTemplate;
                this.htmlSanitizer = htmlSanitizer;
                this.getUnreadMessageUseCase = getUnreadMessageUseCase;
        }

        private UUID currentUserId() {
                var auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()
                                && auth.getPrincipal() instanceof org.springframework.security.core.userdetails.UserDetails ud) {
                        return UUID.fromString(ud.getUsername());
                }
                return UUID.fromString(auth.getPrincipal().toString());
        }

        @GetMapping
        public ResponseEntity<ApiResponse<List<ConversationResponse>>> getConversations(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                UUID userId = currentUserId();
                log.debug("getConversations userId={} page={} size={}", userId, page, size);
                List<GetConversationsUseCase.ConversationView> views = getConversationsUseCase.getConversations(
                                new GetConversationsUseCase.Query(userId, page, size));

                List<ConversationResponse> responses = views.stream()
                                .map(v -> {
                                        MessageResponse lastMessage = v.lastMessage() != null
                                                        ? MessageResponse.from(v.lastMessage().message(),
                                                                        v.lastMessage().senderUsername(),
                                                                        v.lastMessage().senderAvatarUrl())
                                                        : null;
                                        return ConversationResponse.from(v.conversation(), lastMessage,
                                                        v.unreadCount(), v.eachOtherName());
                                })
                                .toList();
                return ResponseEntity.ok(ApiResponse.ok(responses));
        }

        @PostMapping
        public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
                        @Valid @RequestBody CreateConversationRequest request) {
                UUID creatorId = currentUserId();
                log.info("Conversation created creatorId={} isGroup={}", creatorId, request.isGroup());
                var conversation = createConversationUseCase.createConversation(
                                new CreateConversationUseCase.Command(creatorId, request.participantIds(),
                                                request.name(), request.isGroup()));
                String eachOtherName = null;
                if (!request.isGroup() && !request.participantIds().isEmpty()) {
                        eachOtherName = getUserUseCase
                                        .getUser(new GetUserUseCase.Query(request.participantIds().get(0)))
                                        .getUsername();
                }
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.ok(ConversationResponse.from(conversation, null, 0, eachOtherName)));
        }

        @GetMapping("/{id}/messages")
        public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessages(
                        @PathVariable UUID id,
                        @RequestParam(required = false) String cursor,
                        @RequestParam(defaultValue = "30") int limit) {
                int effectiveLimit = Math.min(limit, 50);
                UUID userId = currentUserId();
                log.debug("getMessages conversationId={} limit={}", id, effectiveLimit);
                UUID cursorId = cursor != null ? UUID.fromString(cursor) : null;
                List<GetMessagesUseCase.MessageView> views = getMessagesUseCase.getMessages(
                                new GetMessagesUseCase.Query(id, userId, cursorId, effectiveLimit));
                List<MessageResponse> responses = views.stream()
                                .map(v -> MessageResponse.from(v.message(), v.senderUsername(), v.senderAvatarUrl()))
                                .toList();
                return ResponseEntity.ok(ApiResponse.ok(responses));
        }

        @PostMapping("/{id}/messages")
        public ResponseEntity<ApiResponse<MessageResponse>> sendMessage(
                        @PathVariable UUID id,
                        @Valid @RequestBody SendMessageRequest request) {
                UUID userId = currentUserId();
                log.info("Message sent conversationId={} userId={}", id, userId);
                SendMessageUseCase.MessageView view = sendMessageUseCase.sendMessage(
                                new SendMessageUseCase.Command(id, userId, htmlSanitizer.sanitize(request.content()),
                                                request.messageType(),
                                                request.mediaUrl(),
                                                request.sharedPostId()));
                MessageResponse response = MessageResponse.from(view.message(), view.senderUsername(),
                                view.senderAvatarUrl());
                messagingTemplate.convertAndSend("/topic/conversations/" + id, response);
                
                return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
        }

        @PutMapping("/{id}/read")
        public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable("id") UUID conversationId,
                        @Valid @RequestBody MarkReadRequest request) {
                log.info("markRead conversationId={}", conversationId);
                int unreadCount = getUnreadMessageUseCase.getUnreadMessage(conversationId, currentUserId());
                markReadUseCase.markRead(
                                new MarkReadUseCase.Command(conversationId, currentUserId(), request.messageId()));
                

                MarkReadResponse res = new MarkReadResponse(conversationId, 0);
                
                messagingTemplate.convertAndSend("/user/${userId}/topic/unread-count", res);
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.ok(null));
        }

        @PostMapping("/{id}/members")
        public ResponseEntity<ApiResponse<Void>> addGroupMember(
                        @PathVariable UUID id,
                        @Valid @RequestBody AddGroupMemberRequest request) {
                addGroupMemberUseCase.addGroupMember(
                                new AddGroupMemberUseCase.Command(id, currentUserId(), request.memberIds()));
                log.info("Group member added conversationId={}", id);
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.ok(null));
        }

        @DeleteMapping("/{id}/members/me")
        public ResponseEntity<ApiResponse<Void>> leaveConversation(@PathVariable UUID id) {
                leaveConversationUseCase.leaveConversation(
                                new LeaveConversationUseCase.Command(id, currentUserId()));
                log.info("User left conversation id={}", id);
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.ok(null));
        }

}
