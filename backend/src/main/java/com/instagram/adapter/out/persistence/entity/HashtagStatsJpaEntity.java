package com.instagram.adapter.out.persistence.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "hashtag_stats")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class HashtagStatsJpaEntity {

    @Id
    @Column(name = "hashtag_id")
    private UUID hashtagId;

    @Column(name = "weekly_count", nullable = false)
    private int weeklyCount;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
