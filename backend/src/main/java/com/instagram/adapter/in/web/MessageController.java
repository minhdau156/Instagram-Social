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
import com.instagram.adapter.in.web.dto.request.SendMessageRequest;
import com.instagram.adapter.in.web.dto.response.ApiResponse;
import com.instagram.adapter.in.web.dto.response.ConversationResponse;
import com.instagram.adapter.in.web.dto.response.MessageResponse;
import com.instagram.domain.port.in.messaging.AddGroupMemberUseCase;
import com.instagram.domain.port.in.messaging.CreateConversationUseCase;
import com.instagram.domain.port.in.messaging.GetConversationsUseCase;
import com.instagram.domain.port.in.messaging.GetMessagesUseCase;
import com.instagram.domain.port.in.messaging.LeaveConversationUseCase;
import com.instagram.domain.port.in.messaging.MarkReadUseCase;
import com.instagram.domain.port.in.messaging.SendMessageUseCase;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@Tag(name = "Messaging", description = "Conversation and message management")
@RequestMapping("/api/v1/conversations")
@RestController
public class MessageController {

    private final GetConversationsUseCase getConversationsUseCase;
    private final CreateConversationUseCase createConversationUseCase;
    private final GetMessagesUseCase getMessagesUseCase;
    private final SendMessageUseCase sendMessageUseCase;
    private final MarkReadUseCase markReadUseCase;
    private final AddGroupMemberUseCase addGroupMemberUseCase;
    private final LeaveConversationUseCase leaveConversationUseCase;

    public MessageController(
            GetConversationsUseCase getConversationsUseCase,
            CreateConversationUseCase createConversationUseCase,
            GetMessagesUseCase getMessagesUseCase,
            SendMessageUseCase sendMessageUseCase,
            MarkReadUseCase markReadUseCase,
            AddGroupMemberUseCase addGroupMemberUseCase,
            LeaveConversationUseCase leaveConversationUseCase) {
        this.getConversationsUseCase = getConversationsUseCase;
        this.createConversationUseCase = createConversationUseCase;
        this.getMessagesUseCase = getMessagesUseCase;
        this.sendMessageUseCase = sendMessageUseCase;
        this.markReadUseCase = markReadUseCase;
        this.addGroupMemberUseCase = addGroupMemberUseCase;
        this.leaveConversationUseCase = leaveConversationUseCase;
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
        List<GetConversationsUseCase.ConversationView> views = getConversationsUseCase.getConversations(
                new GetConversationsUseCase.Query(userId, page, size));
        List<ConversationResponse> responses = views.stream()
                .map(v -> ConversationResponse.from(v.conversation(), null, v.unreadCount()))
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(responses));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> createConversation(
            @Valid @RequestBody CreateConversationRequest request) {
        UUID creatorId = currentUserId();
        var conversation = createConversationUseCase.createConversation(
                new CreateConversationUseCase.Command(creatorId, request.participantIds(), request.name(), request.isGroup()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(ConversationResponse.from(conversation, null, 0)));
    }

    @GetMapping("/{id}/messages")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> getMessages(
            @PathVariable UUID id,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") int limit) {
        int effectiveLimit = Math.min(limit, 50);
        UUID userId = currentUserId();
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
        SendMessageUseCase.MessageView view = sendMessageUseCase.sendMessage(
                new SendMessageUseCase.Command(id, userId, request.content(), request.messageType(),
                        request.mediaUrl(), request.sharedPostId()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(MessageResponse.from(view.message(), view.senderUsername(), view.senderAvatarUrl())));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable UUID id) {
        markReadUseCase.markRead(new MarkReadUseCase.Command(id, currentUserId()));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.ok(null));
    }

    @PostMapping("/{id}/members")
    public ResponseEntity<ApiResponse<Void>> addGroupMember(
            @PathVariable UUID id,
            @Valid @RequestBody AddGroupMemberRequest request) {
        addGroupMemberUseCase.addGroupMember(
                new AddGroupMemberUseCase.Command(id, currentUserId(), request.memberIds()));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.ok(null));
    }

    @DeleteMapping("/{id}/members/me")
    public ResponseEntity<ApiResponse<Void>> leaveConversation(@PathVariable UUID id) {
        leaveConversationUseCase.leaveConversation(
                new LeaveConversationUseCase.Command(id, currentUserId()));
        return ResponseEntity.status(HttpStatus.NO_CONTENT).body(ApiResponse.ok(null));
    }
}
