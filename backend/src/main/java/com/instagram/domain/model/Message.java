package com.instagram.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

public class Message {

    public enum MessageType {
        TEXT, IMAGE, VIDEO, POST_SHARE
    }

    public enum MessageStatus {
        SENT, DELIVERED, READ
    }

    private UUID id;
    private UUID conversationId;
    private UUID senderId;
    private String content;
    private MessageType messageType;
    private String mediaUrl;
    private UUID sharedPostId;
    private MessageStatus status;
    private UUID replyToMessageId;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    private Message() {

    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Message message = new Message();

        public Builder id(UUID id) {
            message.id = id;
            return this;
        }

        public Builder conversationId(UUID conversationId) {
            message.conversationId = conversationId;
            return this;
        }

        public Builder senderId(UUID senderId) {
            message.senderId = senderId;
            return this;
        }

        public Builder content(String content) {
            message.content = content;
            return this;
        }

        public Builder messageType(MessageType messageType) {
            message.messageType = messageType;
            return this;
        }

        public Builder mediaUrl(String mediaUrl) {
            message.mediaUrl = mediaUrl;
            return this;
        }

        public Builder sharedPostId(UUID sharedPostId) {
            message.sharedPostId = sharedPostId;
            return this;
        }

        public Builder status(MessageStatus status) {
            message.status = status;
            return this;
        }

        public Builder createdAt(OffsetDateTime createdAt) {
            message.createdAt = createdAt;
            return this;
        }

        public Builder updatedAt(OffsetDateTime updatedAt) {
            message.updatedAt = updatedAt;
            return this;
        }

        public Builder replyToMessageId(UUID replyToMessageId) {
            message.replyToMessageId = replyToMessageId;
            return this;
        }

        public Message build() {
            return message;
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getConversationId() {
        return conversationId;
    }

    public UUID getSenderId() {
        return senderId;
    }

    public String getContent() {
        return content;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public String getMediaUrl() {
        return mediaUrl;
    }

    public UUID getSharedPostId() {
        return sharedPostId;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public UUID getReplyToMessageId() {
        return replyToMessageId;
    }

    private Builder copy() {
        return this.builder()
                .id(id)
                .conversationId(conversationId)
                .senderId(senderId)
                .content(content)
                .messageType(messageType)
                .mediaUrl(mediaUrl)
                .sharedPostId(sharedPostId)
                .status(status)
                .createdAt(createdAt)
                .updatedAt(updatedAt)
                .replyToMessageId(replyToMessageId);
    }

    public Message withRead() {
        return this.copy()
                .status(MessageStatus.READ)
                .updatedAt(OffsetDateTime.now())
                .build();
    }
}
