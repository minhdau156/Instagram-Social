package com.instagram.adapter.out.persistence.entity;

import com.instagram.domain.model.PostStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity mapping to the {@code posts} table.
 *
 * <p>
 * Lives exclusively in the persistence adapter — the domain layer never sees
 * this class.
 * </p>
 */
@Entity
@Table(name = "posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostJpaEntity extends BaseJpaEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "caption", columnDefinition = "TEXT")
    private String caption;

    @Column(name = "location", length = 255)
    private String location;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "status", length = 50, nullable = false)
    private PostStatus status;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "save_count", nullable = false)
    private int saveCount;

    @Column(name = "share_count", nullable = false)
    private int shareCount;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @PrePersist
    protected void onPrePersist() {
        if (status == null)
            status = PostStatus.PUBLISHED;
    }

}
