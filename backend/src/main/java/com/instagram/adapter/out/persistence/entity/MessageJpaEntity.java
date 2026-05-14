package com.instagram.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.instagram.domain.model.Message;

@Entity
@Table(name = "messages")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "conversation_id", nullable = false, columnDefinition = "uuid")
    private UUID conversationId;

    @Column(name = "sender_id", nullable = false, columnDefinition = "uuid")
    private UUID senderId;

    @Column(name = "body", columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "message_type", nullable = false)
    private Message.MessageType messageType;

    @Column(name = "media_url", columnDefinition = "text")
    private String mediaUrl;

    @Column(name = "shared_post_id", columnDefinition = "uuid")
    private UUID sharedPostId;

    @Column(name = "created_at", nullable = false)
    @CreationTimestamp
    private OffsetDateTime createdAt;

    @Column(name = "reply_to_message_id", columnDefinition = "uuid")
    private UUID replyToMessageId;

    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted;

    @Column(name = "updated_at", nullable = false)
    @UpdateTimestamp
    private OffsetDateTime updatedAt;
}
