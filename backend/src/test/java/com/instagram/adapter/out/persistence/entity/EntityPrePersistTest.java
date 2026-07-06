package com.instagram.adapter.out.persistence.entity;

import com.instagram.domain.model.Notification;
import com.instagram.domain.model.PostStatus;
import com.instagram.domain.model.ReportEntityType;
import com.instagram.domain.model.ReportStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EntityPrePersistTest {

    // ── PostJpaEntity ─────────────────────────────────────────────────────────

    @Test
    void postEntity_prePersist_setsDefaultStatusWhenNull() {
        PostJpaEntity post = new PostJpaEntity();
        post.onPrePersist();
        assertThat(post.getStatus()).isEqualTo(PostStatus.PUBLISHED);
    }

    @Test
    void postEntity_prePersist_doesNotOverwriteExistingStatus() {
        PostJpaEntity post = PostJpaEntity.builder()
                .status(PostStatus.DRAFT)
                .build();
        post.onPrePersist();
        assertThat(post.getStatus()).isEqualTo(PostStatus.DRAFT);
    }

    @Test
    void postEntity_builder_setsAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        PostJpaEntity post = PostJpaEntity.builder()
                .id(id)
                .userId(userId)
                .caption("Hello world")
                .location("Hanoi")
                .status(PostStatus.PUBLISHED)
                .viewCount(10L)
                .likeCount(5)
                .commentCount(3)
                .saveCount(1)
                .shareCount(2)
                .build();

        assertThat(post.getId()).isEqualTo(id);
        assertThat(post.getUserId()).isEqualTo(userId);
        assertThat(post.getCaption()).isEqualTo("Hello world");
        assertThat(post.getLocation()).isEqualTo("Hanoi");
        assertThat(post.getViewCount()).isEqualTo(10L);
        assertThat(post.getLikeCount()).isEqualTo(5);
        assertThat(post.getCommentCount()).isEqualTo(3);
        assertThat(post.getSaveCount()).isEqualTo(1);
        assertThat(post.getShareCount()).isEqualTo(2);
    }

    // ── NotificationJpaEntity ─────────────────────────────────────────────────

    @Test
    void notificationEntity_prePersist_setsCreatedAtAndIsRead() {
        NotificationJpaEntity notification = new NotificationJpaEntity();
        notification.onCreate();

        assertThat(notification.getCreatedAt()).isNotNull();
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    void notificationEntity_prePersist_setsCreatedAtToNow() {
        NotificationJpaEntity notification = new NotificationJpaEntity();
        OffsetDateTime before = OffsetDateTime.now();
        notification.onCreate();
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(notification.getCreatedAt()).isBetween(before, after);
    }

    @Test
    void notificationEntity_builder_setsFields() {
        UUID recipient = UUID.randomUUID();
        UUID actor = UUID.randomUUID();
        NotificationJpaEntity notification = NotificationJpaEntity.builder()
                .recipientId(recipient)
                .actorId(actor)
                .entityType("POST")
                .type(Notification.NotificationType.LIKE_POST)
                .isRead(false)
                .build();

        assertThat(notification.getRecipientId()).isEqualTo(recipient);
        assertThat(notification.getActorId()).isEqualTo(actor);
        assertThat(notification.getEntityType()).isEqualTo("POST");
        assertThat(notification.getType()).isEqualTo(Notification.NotificationType.LIKE_POST);
    }

    // ── UploadSessionJpaEntity ────────────────────────────────────────────────

    @Test
    void uploadSessionEntity_prePersist_setsDefaultsWhenNull() {
        UploadSessionJpaEntity session = new UploadSessionJpaEntity();
        OffsetDateTime before = OffsetDateTime.now();
        session.onCreate();
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(session.getCreatedAt()).isBetween(before, after);
        assertThat(session.getExpiresAt()).isAfter(session.getCreatedAt());
        assertThat(session.getStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    void uploadSessionEntity_prePersist_doesNotOverwriteExistingStatus() {
        UploadSessionJpaEntity session = UploadSessionJpaEntity.builder()
                .status("COMPLETED")
                .createdAt(OffsetDateTime.now())
                .expiresAt(OffsetDateTime.now().plusHours(1))
                .build();
        session.onCreate();
        assertThat(session.getStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void uploadSessionEntity_prePersist_expiresAt24HoursAfterCreatedAt() {
        UploadSessionJpaEntity session = new UploadSessionJpaEntity();
        session.onCreate();

        long hoursBetween = ChronoUnit.HOURS.between(session.getCreatedAt(), session.getExpiresAt());
        assertThat(hoursBetween).isEqualTo(24L);
    }

    @Test
    void uploadSessionEntity_builder_setsAllFields() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UploadSessionJpaEntity session = UploadSessionJpaEntity.builder()
                .id(id)
                .uploadId("upload-123")
                .objectKey("images/photo.jpg")
                .contentType("image/jpeg")
                .userId(userId)
                .totalParts(5)
                .status("IN_PROGRESS")
                .build();

        assertThat(session.getId()).isEqualTo(id);
        assertThat(session.getUploadId()).isEqualTo("upload-123");
        assertThat(session.getObjectKey()).isEqualTo("images/photo.jpg");
        assertThat(session.getContentType()).isEqualTo("image/jpeg");
        assertThat(session.getUserId()).isEqualTo(userId);
        assertThat(session.getTotalParts()).isEqualTo(5);
    }

    // ── IdempotencyKeyJpaEntity ───────────────────────────────────────────────

    @Test
    void idempotencyKeyEntity_prePersist_setsCreatedAtWhenNull() {
        IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity();
        OffsetDateTime before = OffsetDateTime.now();
        entity.prePersist();
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(entity.getCreatedAt()).isBetween(before, after);
    }

    @Test
    void idempotencyKeyEntity_prePersist_doesNotOverwriteExistingCreatedAt() {
        OffsetDateTime existing = OffsetDateTime.now().minusDays(1);
        IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity();
        entity.setCreatedAt(existing);
        entity.prePersist();

        assertThat(entity.getCreatedAt()).isEqualTo(existing);
    }

    @Test
    void idempotencyKeyEntity_settersAndGetters() {
        UUID key = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        IdempotencyKeyJpaEntity entity = new IdempotencyKeyJpaEntity();
        entity.setKey(key);
        entity.setUserId(userId);
        entity.setEndpoint("/api/v1/posts");
        entity.setRequestHash("abc123");
        entity.setResponseBody("{\"id\":\"123\"}");
        entity.setHttpStatus(201);

        assertThat(entity.getKey()).isEqualTo(key);
        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getEndpoint()).isEqualTo("/api/v1/posts");
        assertThat(entity.getRequestHash()).isEqualTo("abc123");
        assertThat(entity.getResponseBody()).isEqualTo("{\"id\":\"123\"}");
        assertThat(entity.getHttpStatus()).isEqualTo(201);
    }

    // ── UserRoleJpaEntity ─────────────────────────────────────────────────────

    @Test
    void userRoleEntity_prePersist_setsAssignedAtWhenNull() {
        UserRoleJpaEntity entity = new UserRoleJpaEntity();
        OffsetDateTime before = OffsetDateTime.now();
        entity.onCreate();
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(entity.getAssignedAt()).isBetween(before, after);
    }

    @Test
    void userRoleEntity_prePersist_doesNotOverwriteExistingAssignedAt() {
        OffsetDateTime existing = OffsetDateTime.now().minusDays(1);
        UserRoleJpaEntity entity = UserRoleJpaEntity.builder()
                .assignedAt(existing)
                .build();
        entity.onCreate();

        assertThat(entity.getAssignedAt()).isEqualTo(existing);
    }

    // ── AuditLogJpaEntity ─────────────────────────────────────────────────────

    @Test
    void auditLogEntity_prePersist_setsCreatedAtWhenNull() {
        AuditLogJpaEntity entity = new AuditLogJpaEntity();
        OffsetDateTime before = OffsetDateTime.now();
        entity.onCreate();
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(entity.getCreatedAt()).isBetween(before, after);
    }

    @Test
    void auditLogEntity_prePersist_doesNotOverwriteExistingCreatedAt() {
        OffsetDateTime existing = OffsetDateTime.now().minusHours(2);
        AuditLogJpaEntity entity = AuditLogJpaEntity.builder()
                .createdAt(existing)
                .build();
        entity.onCreate();

        assertThat(entity.getCreatedAt()).isEqualTo(existing);
    }

    @Test
    void auditLogEntity_builder_setsAllFields() {
        UUID userId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        AuditLogJpaEntity entity = AuditLogJpaEntity.builder()
                .userId(userId)
                .action("CREATE_POST")
                .entityType("POST")
                .entityId(entityId)
                .metadata("{\"postId\":\"123\"}")
                .ipAddress("127.0.0.1")
                .build();

        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getAction()).isEqualTo("CREATE_POST");
        assertThat(entity.getEntityType()).isEqualTo("POST");
        assertThat(entity.getEntityId()).isEqualTo(entityId);
        assertThat(entity.getMetadata()).isEqualTo("{\"postId\":\"123\"}");
        assertThat(entity.getIpAddress()).isEqualTo("127.0.0.1");
    }

    // ── UserBlockJpaEntity ────────────────────────────────────────────────────

    @Test
    void userBlockEntity_prePersist_setsCreatedAtWhenNull() {
        UserBlockJpaEntity entity = new UserBlockJpaEntity();
        OffsetDateTime before = OffsetDateTime.now();
        entity.onCreate();
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(entity.getCreatedAt()).isBetween(before, after);
    }

    @Test
    void userBlockEntity_prePersist_doesNotOverwriteExistingCreatedAt() {
        OffsetDateTime existing = OffsetDateTime.now().minusHours(1);
        UserBlockId id = new UserBlockId(UUID.randomUUID(), UUID.randomUUID());
        UserBlockJpaEntity entity = UserBlockJpaEntity.builder()
                .id(id)
                .createdAt(existing)
                .build();
        entity.onCreate();

        assertThat(entity.getCreatedAt()).isEqualTo(existing);
    }

    @Test
    void userBlockEntity_builder_setsId() {
        UUID blockerId = UUID.randomUUID();
        UUID blockedId = UUID.randomUUID();
        UserBlockId id = new UserBlockId(blockerId, blockedId);
        UserBlockJpaEntity entity = UserBlockJpaEntity.builder().id(id).build();

        assertThat(entity.getId().getBlockerId()).isEqualTo(blockerId);
        assertThat(entity.getId().getBlockedId()).isEqualTo(blockedId);
    }

    // ── SearchHistoryJpaEntity ────────────────────────────────────────────────

    @Test
    void searchHistoryEntity_prePersist_setsSearchedAtWhenNull() {
        SearchHistoryJpaEntity entity = new SearchHistoryJpaEntity();
        OffsetDateTime before = OffsetDateTime.now();
        entity.onCreate();
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(entity.getSearchedAt()).isBetween(before, after);
    }

    @Test
    void searchHistoryEntity_prePersist_doesNotOverwriteExistingSearchedAt() {
        OffsetDateTime existing = OffsetDateTime.now().minusMinutes(10);
        SearchHistoryJpaEntity entity = SearchHistoryJpaEntity.builder()
                .userId(UUID.randomUUID())
                .query("cats")
                .searchedAt(existing)
                .build();
        entity.onCreate();

        assertThat(entity.getSearchedAt()).isEqualTo(existing);
    }

    @Test
    void searchHistoryEntity_builder_setsAllFields() {
        UUID userId = UUID.randomUUID();
        SearchHistoryJpaEntity entity = SearchHistoryJpaEntity.builder()
                .userId(userId)
                .query("spring boot")
                .build();

        assertThat(entity.getUserId()).isEqualTo(userId);
        assertThat(entity.getQuery()).isEqualTo("spring boot");
    }

    // ── ReportJpaEntity ───────────────────────────────────────────────────────

    @Test
    void reportEntity_prePersist_setsCreatedAtAndDefaultStatusWhenNull() {
        ReportJpaEntity entity = new ReportJpaEntity();
        OffsetDateTime before = OffsetDateTime.now();
        entity.onCreate();
        OffsetDateTime after = OffsetDateTime.now();

        assertThat(entity.getCreatedAt()).isBetween(before, after);
        assertThat(entity.getStatus()).isEqualTo(ReportStatus.PENDING);
    }

    @Test
    void reportEntity_prePersist_doesNotOverwriteExistingStatus() {
        ReportJpaEntity entity = ReportJpaEntity.builder()
                .status(ReportStatus.REVIEWED)
                .createdAt(OffsetDateTime.now())
                .build();
        entity.onCreate();

        assertThat(entity.getStatus()).isEqualTo(ReportStatus.REVIEWED);
    }

    @Test
    void reportEntity_prePersist_doesNotOverwriteExistingCreatedAt() {
        OffsetDateTime existing = OffsetDateTime.now().minusDays(1);
        ReportJpaEntity entity = ReportJpaEntity.builder()
                .createdAt(existing)
                .build();
        entity.onCreate();

        assertThat(entity.getCreatedAt()).isEqualTo(existing);
    }

    @Test
    void reportEntity_builder_setsAllFields() {
        UUID reporterId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();
        ReportJpaEntity entity = ReportJpaEntity.builder()
                .reporterId(reporterId)
                .entityType(ReportEntityType.POST)
                .entityId(entityId)
                .reason("spam")
                .details("This post is spam")
                .status(ReportStatus.PENDING)
                .build();

        assertThat(entity.getReporterId()).isEqualTo(reporterId);
        assertThat(entity.getEntityType()).isEqualTo(ReportEntityType.POST);
        assertThat(entity.getEntityId()).isEqualTo(entityId);
        assertThat(entity.getReason()).isEqualTo("spam");
        assertThat(entity.getDetails()).isEqualTo("This post is spam");
        assertThat(entity.getStatus()).isEqualTo(ReportStatus.PENDING);
    }
}
