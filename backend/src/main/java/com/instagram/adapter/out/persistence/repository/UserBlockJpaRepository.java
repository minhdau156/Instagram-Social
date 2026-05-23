package com.instagram.adapter.out.persistence.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.adapter.out.persistence.entity.UserBlockId;
import com.instagram.adapter.out.persistence.entity.UserBlockJpaEntity;

public interface UserBlockJpaRepository extends JpaRepository<UserBlockJpaEntity, UserBlockId> {
        boolean existsById(UserBlockId id);

        @Query("SELECT CASE WHEN COUNT(b) > 0 THEN TRUE ELSE FALSE END "
                        + " FROM UserBlockJpaEntity b WHERE b.id.blockerId = :blockerId AND b.id.blockedId = :blockedId")
        boolean existsByBlockerIdAndBlockedId(@Param("blockerId") UUID blockerId,
                        @Param("blockedId") UUID blockedId);

        @Modifying
        @Transactional
        @Query("DELETE FROM UserBlockJpaEntity b "
                        + "WHERE b.id.blockerId = :blockerId AND b.id.blockedId = :blockedId")
        void deleteByBlockerIdAndBlockedId(@Param("blockerId") UUID blockerId,
                        @Param("blockedId") UUID blockedId);

        Page<UserBlockJpaEntity> findByIdBlockerIdOrderByCreatedAtDesc(UUID blockerId, Pageable pageable);

        @Query("SELECT b.id.blockedId FROM UserBlockJpaEntity b WHERE b.id.blockerId = :blockerId")
        List<UUID> findBlockedIdsByBlockerId(@Param("blockerId") UUID blockerId);

        @Query("SELECT b.id.blockerId FROM UserBlockJpaEntity b WHERE b.id.blockedId = :blockedId")
        List<UUID> findBlockerIdsByBlockedId(@Param("blockedId") UUID blockedId);

}
