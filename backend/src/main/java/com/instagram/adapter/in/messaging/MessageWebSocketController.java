package com.instagram.adapter.in.messaging;

import java.security.Principal;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.instagram.adapter.in.web.dto.request.SendMessageRequest;
import com.instagram.domain.exception.NotConversationMemberException;
import com.instagram.domain.port.in.messaging.SendMessageUseCase;

@Controller
public class MessageWebSocketController {
    private final SendMessageUseCase sendMessageUseCase;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public MessageWebSocketController(SendMessageUseCase sendMessageUseCase,
            SimpMessagingTemplate simpMessagingTemplate) {
        this.sendMessageUseCase = sendMessageUseCase;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void handleSend(SendMessageRequest request, Principal principal) {
        UUID senderId = UUID.fromString(principal.getName());
        SendMessageUseCase.Command command = new SendMessageUseCase.Command(
                request.conversationId(), senderId, request.content(),
                request.messageType(), request.mediaUrl(), request.sharedPostId());
        try {
            sendMessageUseCase.sendMessage(command);
        } catch (NotConversationMemberException e) {
            simpMessagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", e.getMessage());
        }
    }

    @MessageMapping("/chat.typing")
    public void handleTyping(TypingPayload payload, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        UUID conversationId = payload.conversationId();
        simpMessagingTemplate.convertAndSend("/topic/conversations/" + conversationId + "/typing",
                new TypingEvent(conversationId, userId, payload.isTyping()));
    }
}
