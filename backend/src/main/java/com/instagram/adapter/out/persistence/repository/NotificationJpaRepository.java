package com.instagram.adapter.out.persistence.repository;

import com.instagram.adapter.out.persistence.entity.NotificationJpaEntity;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, UUID> {
  Page<NotificationJpaEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

  @Transactional
  @Modifying(clearAutomatically = true)
  @Query("UPDATE NotificationJpaEntity n SET n.isRead = true WHERE n.id = :id")
  void markAsRead(@Param("id") UUID id);

  @Transactional
  @Modifying(clearAutomatically = true)
  @Query("UPDATE NotificationJpaEntity n SET n.isRead = true WHERE n.recipientId = :recipientId")
  void markAllAsRead(@Param("recipientId") UUID recipientId);

  long countByRecipientIdAndIsReadFalse(UUID recipientId);

  @Query(value = """
      SELECT n.* FROM notifications n
      WHERE n.recipient_id = :recipientId
        AND (
            :cursorTs IS NULL
            OR (n.created_at, n.id) < (CAST(:cursorTs AS timestamptz), CAST(:cursorId AS uuid))
        )
      ORDER BY n.created_at DESC, n.id DESC
      LIMIT :size
      """, nativeQuery = true)
  List<NotificationJpaEntity> findByRecipientIdKeysetBefore(
      @Param("recipientId") UUID recipientId,
      @Param("cursorTs") String cursorTs,
      @Param("cursorId") UUID cursorId,
      @Param("size") int size);
}