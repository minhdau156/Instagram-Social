package com.instagram.adapter.out.persistence.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.instagram.adapter.out.persistence.entity.HashtagStatsJpaEntity;

@Repository
public interface HashtagStatsJpaRepository
        extends JpaRepository<HashtagStatsJpaEntity, UUID> {

    @Modifying
    @Transactional
    @Query(value = """
            INSERT INTO hashtag_stats (hashtag_id, weekly_count, updated_at)
            SELECT ph.hashtag_id,
                   COUNT(*) AS weekly_count,
                   NOW()
            FROM post_hashtags ph
            JOIN posts p ON p.id = ph.post_id
            WHERE p.created_at >= NOW() - INTERVAL '7 days'
              AND p.deleted_at IS NULL
            GROUP BY ph.hashtag_id
            ON CONFLICT (hashtag_id)
            DO UPDATE SET weekly_count = EXCLUDED.weekly_count,
                          updated_at   = NOW()
            """, nativeQuery = true)
    void rollupWeeklyCounts();
}
