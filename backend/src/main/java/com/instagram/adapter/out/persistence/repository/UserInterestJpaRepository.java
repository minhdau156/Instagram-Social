package com.instagram.adapter.out.persistence.repository;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.instagram.adapter.out.persistence.entity.UserInterestJpaEntity;
import com.instagram.adapter.out.persistence.entity.UserInterestId;

import org.springframework.transaction.annotation.Transactional;

@Repository
public interface UserInterestJpaRepository
        extends JpaRepository<UserInterestJpaEntity, UserInterestId> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO user_interests (user_id, hashtag_id, score, updated_at)
            VALUES (:userId, :hashtagId, :delta, NOW())
            ON CONFLICT (user_id, hashtag_id)
            DO UPDATE SET score = user_interests.score + :delta,
                          updated_at = NOW()
            """, nativeQuery = true)
    void upsertScore(@Param("userId") UUID userId,
            @Param("hashtagId") UUID hashtagId,
            @Param("delta") BigDecimal delta);
}
