package com.instagram.adapter.out.persistence.entity;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "notification_settings")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationSettingsJpaEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "likes", nullable = false)
    private boolean likesEnabled;

    @Column(name = "comments", nullable = false)
    private boolean commentsEnabled;

    @Column(name = "new_followers", nullable = false)
    private boolean followsEnabled;

    @Column(name = "direct_messages", nullable = false)
    private boolean messagesEnabled;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;
}
