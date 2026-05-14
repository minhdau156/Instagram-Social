package com.instagram.adapter.out.persistence.entity;

import java.time.OffsetDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "message_reads")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageReadJpaEntity {
    @EmbeddedId
    private MessageReadId id;

    @Column(name = "read_at", nullable = false)
    @CreationTimestamp
    private OffsetDateTime readAt;
}
